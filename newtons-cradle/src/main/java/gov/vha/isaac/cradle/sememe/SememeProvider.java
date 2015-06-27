/*
 * Copyright 2015 kec.
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
package gov.vha.isaac.cradle.sememe;

import gov.vha.isaac.cradle.Cradle;
import gov.vha.isaac.cradle.waitfree.CasSequenceObjectMap;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.IdentifierService;
import gov.vha.isaac.ochre.api.SystemStatusService;
import gov.vha.isaac.ochre.api.commit.CommitService;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampPosition;
import gov.vha.isaac.ochre.api.component.sememe.SememeChronology;
import gov.vha.isaac.ochre.api.component.sememe.SememeService;
import gov.vha.isaac.ochre.api.component.sememe.SememeServiceTyped;
import gov.vha.isaac.ochre.api.component.sememe.SememeSnapshotService;
import gov.vha.isaac.ochre.api.component.sememe.version.DescriptionSememe;
import gov.vha.isaac.ochre.api.component.sememe.version.SememeVersion;
import gov.vha.isaac.ochre.collections.NidSet;
import gov.vha.isaac.ochre.collections.SememeSequenceSet;
import gov.vha.isaac.ochre.model.sememe.SememeChronologyImpl;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author kec
 */
@Service
@RunLevel(value = 0)
public class SememeProvider implements SememeService {

    private static final Logger log = LogManager.getLogger();

    private static CommitService commitService;

    private static CommitService getCommitService() {
        if (commitService == null) {
            commitService = LookupService.getService(CommitService.class);
        }
        return commitService;
    }

    final CasSequenceObjectMap<SememeChronologyImpl<?>> sememeMap;
    final ConcurrentSkipListSet<AssemblageSememeKey> assemblageSequenceSememeSequenceMap = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<ReferencedNidAssemblageSequenceSememeSequenceKey> referencedNidAssemblageSequenceSememeSequenceMap = new ConcurrentSkipListSet<>();
    final IdentifierService identifierService;

    //For HK2
    private SememeProvider() throws IOException {
        try {
            identifierService = LookupService.getService(IdentifierService.class);

            Path sememePath = Cradle.getCradlePath().resolve("sememe");
            log.info("Setting up sememe provider at " + sememePath.toAbsolutePath().toString());

            sememeMap = new CasSequenceObjectMap(new SememeSerializer(), sememePath, "seg.", ".sememe.map");
        } catch (Exception e) {
            LookupService.getService(SystemStatusService.class).notifyServiceConfigurationFailure("Cradle Commit Manager", e);
            throw e;
        }
    }

    @PostConstruct
    private void startMe() throws IOException {
        try {
            log.info("Loading sememeMap.");
            if (!Cradle.cradleStartedEmpty()) {
                log.info("Reading sememeMap.");
                sememeMap.initialize();

                log.info("Loading SememeKeys.");

                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(Cradle.getCradlePath().toFile(), "assemblage-sememe.keys"))))) {
                    int size = in.readInt();
                    for (int i = 0; i < size; i++) {
                        int assemblageSequence = in.readInt();
                        int sequence = in.readInt();
                        assemblageSequenceSememeSequenceMap.add(new AssemblageSememeKey(assemblageSequence, sequence));
                    }
                }
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(Cradle.getCradlePath().toFile(), "component-sememe.keys"))))) {
                    int size = in.readInt();
                    for (int i = 0; i < size; i++) {
                        int referencedNid = in.readInt();
                        int assemblageSequence = in.readInt();
                        int sequence = in.readInt();
                        referencedNidAssemblageSequenceSememeSequenceMap.add(new ReferencedNidAssemblageSequenceSememeSequenceKey(referencedNid, assemblageSequence, sequence));
                    }
                }
            }

            SememeSequenceSet statedGraphSequences = getSememeSequencesFromAssemblage(identifierService.getConceptSequence(identifierService.getNidForUuids(IsaacMetadataAuxiliaryBinding.EL_PLUS_PLUS_STATED_FORM.getUuids())));
            log.info("Stated logic graphs: " + statedGraphSequences.size());

            SememeSequenceSet inferedGraphSequences = getSememeSequencesFromAssemblage(identifierService.getConceptSequence(identifierService.getNidForUuids(IsaacMetadataAuxiliaryBinding.EL_PLUS_PLUS_INFERRED_FORM.getUuids())));

            log.info("Inferred logic graphs: " + inferedGraphSequences.size());
            log.info("Finished SememeProvider load.");
        } catch (Exception e) {
            LookupService.getService(SystemStatusService.class).notifyServiceConfigurationFailure("Cradle Commit Manager", e);
            throw e;
        }
    }

    @PreDestroy
    private void stopMe() throws IOException {
        log.info("Stopping SememeProvider pre-destroy. ");

        //Dan commented out this log statement because it is really slow...
        //log.info("sememeMap size: {}", sememeMap.getSize());
        log.info("writing sememe-map.");
        sememeMap.write();

        log.info("writing SememeKeys.");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Cradle.getCradlePath().toFile(), "assemblage-sememe.keys"))))) {
            out.writeInt(assemblageSequenceSememeSequenceMap.size());
            for (AssemblageSememeKey key : assemblageSequenceSememeSequenceMap) {
                out.writeInt(key.assemblageSequence);
                out.writeInt(key.sememeSequence);
            }
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Cradle.getCradlePath().toFile(), "component-sememe.keys"))))) {
            out.writeInt(referencedNidAssemblageSequenceSememeSequenceMap.size());
            for (ReferencedNidAssemblageSequenceSememeSequenceKey key : referencedNidAssemblageSequenceSememeSequenceMap) {
                out.writeInt(key.referencedNid);
                out.writeInt(key.assemblageSequence);
                out.writeInt(key.sememeSequence);
            }
        }
        log.info("Finished SememeProvider stop.");
    }

    @Override
    public <V extends SememeVersion> SememeSnapshotService<V> getSnapshot(Class<V> versionType, StampCoordinate stampCoordinate) {
        return new SememeSnapshotProvider<>(versionType, stampCoordinate, this);
    }

    @Override
    public SememeChronology getSememe(int sememeSequence) {
        sememeSequence = identifierService.getSememeSequence(sememeSequence);
        return sememeMap.getQuick(sememeSequence);
    }

    @Override
    public Stream<SememeChronology<? extends SememeVersion>> getSememesFromAssemblage(int assemblageSequence) {
        SememeSequenceSet sememeSequences = getSememeSequencesFromAssemblage(assemblageSequence);
        return sememeSequences.stream().mapToObj((int sememeSequence) -> getSememe(sememeSequence));
    }

    @Override
    public SememeSequenceSet getSememeSequencesFromAssemblage(int assemblageSequence) {
        assemblageSequence = identifierService.getConceptSequence(assemblageSequence);
        AssemblageSememeKey rangeStart = new AssemblageSememeKey(assemblageSequence, Integer.MIN_VALUE); // yes
        AssemblageSememeKey rangeEnd = new AssemblageSememeKey(assemblageSequence, Integer.MAX_VALUE); // no
        NavigableSet<AssemblageSememeKey> assemblageSememeKeys
                = assemblageSequenceSememeSequenceMap.subSet(rangeStart, true,
                        rangeEnd, true
                );
        return SememeSequenceSet.of(assemblageSememeKeys.stream().mapToInt((AssemblageSememeKey key) -> key.sememeSequence));
    }

    @Override
    public Stream<SememeChronology<? extends SememeVersion>> getSememesForComponent(int componentNid) {
        SememeSequenceSet sememeSequences = getSememeSequencesForComponent(componentNid);
        return sememeSequences.stream().mapToObj((int sememeSequence) -> getSememe(sememeSequence));
    }

    @Override
    public SememeSequenceSet getSememeSequencesForComponent(int componentNid) {
        if (componentNid >= 0) {
            throw new IndexOutOfBoundsException("Component identifiers must be negative. Found: " + componentNid);
        }
        NavigableSet<ReferencedNidAssemblageSequenceSememeSequenceKey> assemblageSememeKeys
                = referencedNidAssemblageSequenceSememeSequenceMap.subSet(
                        new ReferencedNidAssemblageSequenceSememeSequenceKey(componentNid, Integer.MIN_VALUE, Integer.MIN_VALUE), true,
                        new ReferencedNidAssemblageSequenceSememeSequenceKey(componentNid, Integer.MAX_VALUE, Integer.MAX_VALUE), true
                );
        return SememeSequenceSet.of(assemblageSememeKeys.stream().mapToInt((ReferencedNidAssemblageSequenceSememeSequenceKey key) -> key.sememeSequence));
    }

    @Override
    public Stream<SememeChronology<? extends SememeVersion>> getSememesForComponentFromAssemblage(int componentNid, int assemblageSequence) {
        if (componentNid >= 0) {
            componentNid = identifierService.getConceptNid(componentNid);
        }
        if (assemblageSequence < 0) {
            assemblageSequence = identifierService.getConceptSequence(assemblageSequence);
        }
        SememeSequenceSet sememeSequences = getSememeSequencesForComponentFromAssemblage(componentNid, assemblageSequence);
        return sememeSequences.stream().mapToObj((int sememeSequence) -> getSememe(sememeSequence));
    }

    @Override
    public SememeSequenceSet getSememeSequencesForComponentFromAssemblage(int componentNid, int assemblageSequence) {
        if (componentNid >= 0) {
            throw new IndexOutOfBoundsException("Component identifiers must be negative. Found: " + componentNid);
        }
        assemblageSequence = identifierService.getConceptSequence(assemblageSequence);
        ReferencedNidAssemblageSequenceSememeSequenceKey rcRangeStart = new ReferencedNidAssemblageSequenceSememeSequenceKey(componentNid, assemblageSequence, Integer.MIN_VALUE); // yes
        ReferencedNidAssemblageSequenceSememeSequenceKey rcRangeEnd = new ReferencedNidAssemblageSequenceSememeSequenceKey(componentNid, assemblageSequence, Integer.MAX_VALUE); // no
        NavigableSet<ReferencedNidAssemblageSequenceSememeSequenceKey> referencedComponentRefexKeys
                = referencedNidAssemblageSequenceSememeSequenceMap.subSet(rcRangeStart, true,
                        rcRangeEnd, true
                );

        SememeSequenceSet referencedComponentSet
                = SememeSequenceSet.of(referencedComponentRefexKeys.stream()
                        .mapToInt((ReferencedNidAssemblageSequenceSememeSequenceKey key) -> key.sememeSequence));

        return referencedComponentSet;
    }

    @Override
    public SememeSequenceSet getSememeSequencesForComponentsFromAssemblage(NidSet componentNidSet, final int assemblageSequence) {
        if (assemblageSequence < 0) {
            throw new IndexOutOfBoundsException("assemblageSequence must be >= 0. Found: " + assemblageSequence);
        }
        SememeSequenceSet resultSet = new SememeSequenceSet();
        componentNidSet.stream().forEach((componentNid) -> {
            ReferencedNidAssemblageSequenceSememeSequenceKey rcRangeStart = new ReferencedNidAssemblageSequenceSememeSequenceKey(componentNid, assemblageSequence, Integer.MIN_VALUE); // yes
            ReferencedNidAssemblageSequenceSememeSequenceKey rcRangeEnd = new ReferencedNidAssemblageSequenceSememeSequenceKey(componentNid, assemblageSequence, Integer.MAX_VALUE); // no
            NavigableSet<ReferencedNidAssemblageSequenceSememeSequenceKey> referencedComponentRefexKeys
                    = referencedNidAssemblageSequenceSememeSequenceMap.subSet(rcRangeStart, true,
                            rcRangeEnd, true
                    );
            referencedComponentRefexKeys.stream().forEach((key) -> {resultSet.add(key.sememeSequence);});
            
        });

        return resultSet;
    }

    @Override
    public void writeSememe(SememeChronology sememeChronicle) {
        assemblageSequenceSememeSequenceMap.add(
                new AssemblageSememeKey(sememeChronicle.getAssemblageSequence(),
                        sememeChronicle.getSememeSequence()));
        referencedNidAssemblageSequenceSememeSequenceMap.add(
                new ReferencedNidAssemblageSequenceSememeSequenceKey(sememeChronicle.getReferencedComponentNid(),
                        sememeChronicle.getAssemblageSequence(),
                        sememeChronicle.getSememeSequence()));
        sememeMap.put(sememeChronicle.getSememeSequence(),
                (SememeChronologyImpl<?>) sememeChronicle);
    }

    @Override
    public SememeSequenceSet getSememeSequencesForComponentsFromAssemblageModifiedAfterPosition(
            NidSet componentNidSet, int assemblageSequence, StampPosition position) {
        SememeSequenceSet sequencesToTest
                = getSememeSequencesForComponentsFromAssemblage(componentNidSet, assemblageSequence);
        SememeSequenceSet sequencesThatPassedTest = new SememeSequenceSet();
        getCommitService();
        sequencesToTest.stream().forEach((sememeSequence) -> {
            SememeChronologyImpl<?> chronicle = (SememeChronologyImpl<?>) getSememe(sememeSequence);
            if (chronicle.getVersionStampSequences().anyMatch((stampSequence) -> {
                return (commitService.getTimeForStamp(stampSequence) > position.getTime()
                        && (position.getStampPathSequence() == commitService.getPathSequenceForStamp(stampSequence)));
            })) {
                sequencesThatPassedTest.add(sememeSequence);
            }
        });
        return sequencesThatPassedTest;
    }

    @Override
    public Stream<SememeChronology<? extends SememeVersion>> getSememeStream() {
        return identifierService.getSememeSequenceStream().mapToObj((int sememeSequence) -> getSememe(sememeSequence));
    }

    @Override
    public Stream<SememeChronology<? extends SememeVersion>> getParallelSememeStream() {
        return identifierService.getSememeSequenceStream().parallel().mapToObj((int sememeSequence) -> {
            try {
                //TODO Keith - this is DEBUG code that should be removed - it isn't proper to inject a null into the return stream.  However, something _ELSE_
                //is broken at the moment, and the sememeSequenceStream is returning invalid sememe identifiers... eek.
                return getSememe(sememeSequence);
            } catch (Exception e) {
                log.error("sememe sequence " + sememeSequence + " could not be resolved!", e);
                return null;
            }
        });
    }
    int descriptionAssemblageSequence = Integer.MIN_VALUE;

    @Override
    public Stream<SememeChronology<DescriptionSememe>> getDescriptionsForComponent(int componentNid) {
        if (descriptionAssemblageSequence == Integer.MIN_VALUE) {
            descriptionAssemblageSequence = identifierService.getConceptSequenceForUuids(IsaacMetadataAuxiliaryBinding.DESCRIPTION_ASSEMBLAGE.getUuids());
        }
        SememeSequenceSet sequences = getSememeSequencesForComponentFromAssemblage(componentNid, descriptionAssemblageSequence);
        return sequences.stream().mapToObj((int sememeSequence) -> getSememe(sememeSequence));
    }

    @Override
    public <V extends SememeVersion> SememeServiceTyped<V> ofType(Class<V> versionType) {
        return new SememeTypeProvider<>(versionType, this);
    }

}