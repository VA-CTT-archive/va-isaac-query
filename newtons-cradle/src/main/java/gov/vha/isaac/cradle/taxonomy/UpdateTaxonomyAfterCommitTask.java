/*
 * Copyright 2015 U.S. Department of Veterans Affairs.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.vha.isaac.cradle.taxonomy;

import gov.vha.isaac.ochre.api.Get;
import gov.vha.isaac.ochre.api.TaxonomyService;
import gov.vha.isaac.ochre.api.commit.CommitRecord;
import gov.vha.isaac.ochre.api.component.sememe.SememeChronology;
import gov.vha.isaac.ochre.api.component.sememe.version.LogicGraphSememe;
import gov.vha.isaac.ochre.api.task.TimedTask;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.StampedLock;

/**
 *
 * @author kec
 */
public class UpdateTaxonomyAfterCommitTask extends TimedTask<Void> {

    TaxonomyService taxonomyService;
    CommitRecord commitRecord;
    ConcurrentSkipListSet<Integer> sememeSequencesForUnhandledChanges;
    StampedLock lock;
    long stamp;
    int workDone = 0;
    int totalWork = 0;

    private UpdateTaxonomyAfterCommitTask(TaxonomyService taxonomyService,
            CommitRecord commitRecord, 
            ConcurrentSkipListSet<Integer> sememeSequencesForUnhandledChanges, 
            StampedLock lock, long stamp) {
        this.commitRecord = commitRecord;
        this.sememeSequencesForUnhandledChanges = sememeSequencesForUnhandledChanges;
        this.lock = lock;
        this.stamp = stamp;
        this.taxonomyService = taxonomyService;
        this.totalWork = sememeSequencesForUnhandledChanges.size();
        this.updateTitle("Update taxonomy after commit");
        this.updateProgress(workDone, totalWork);
    }
    
    /**
     * Get an executing task that will update the taxonomy. 
     * @param taxonomyService the service to update
     * @param commitRecord the commitRecord to process
     * @param unhandledChanges the changes to look for
     * @param lock write lock for the update
     * @param stamp stamp to use to release lock in the finally clause of the call method
     * @return a task, submitted to an executor, and added to the active task set. 
     * 
     */
    public static UpdateTaxonomyAfterCommitTask get(TaxonomyService taxonomyService,
            CommitRecord commitRecord, 
            ConcurrentSkipListSet<Integer> unhandledChanges, 
            StampedLock lock, long stamp) {
        UpdateTaxonomyAfterCommitTask task = 
                new UpdateTaxonomyAfterCommitTask(taxonomyService, commitRecord, unhandledChanges, lock, stamp);
        Get.activeTasks().add(task);
        Get.workExecutors().getExecutor().execute(task);
        return task;
    }

    @Override
    protected Void call() throws Exception {
        try {
            sememeSequencesForUnhandledChanges.stream().forEach((sememeSequence) -> {
                workDone++;
                this.updateProgress(workDone, totalWork);
                if (commitRecord.getSememesInCommit().contains(sememeSequence)) {
                    this.updateMessage("Updating taxonomy for: " + sememeSequence);
                    taxonomyService.updateTaxonomy((SememeChronology<LogicGraphSememe>) Get.sememeService().getSememe(sememeSequence));
                    sememeSequencesForUnhandledChanges.remove(sememeSequence);
                }
            });
            this.updateMessage("complete");
            return null;
        } finally {
            lock.unlockWrite(stamp);
            Get.activeTasks().remove(this);
        }
    }
}
