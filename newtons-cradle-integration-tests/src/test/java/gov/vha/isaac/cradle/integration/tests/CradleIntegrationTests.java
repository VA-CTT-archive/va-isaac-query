/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.vha.isaac.cradle.integration.tests;

import static gov.vha.isaac.ochre.api.constants.Constants.CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY;
import gov.vha.isaac.cradle.taxonomy.walk.TaxonomyWalkAccumulator;
import gov.vha.isaac.cradle.taxonomy.walk.TaxonomyWalkCollector;
import gov.vha.isaac.metadata.coordinates.ViewCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.ConceptModel;
import gov.vha.isaac.ochre.api.ConfigurationService;
import gov.vha.isaac.ochre.api.IdentifierService;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.ObjectChronicleTaskService;
import gov.vha.isaac.ochre.api.TaxonomyService;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.component.concept.ConceptService;
import gov.vha.isaac.ochre.api.coordinate.TaxonomyCoordinate;
import gov.vha.isaac.ochre.api.memory.HeapUseTicker;
import gov.vha.isaac.ochre.api.progress.ActiveTasksTicker;
import gov.vha.isaac.ochre.api.tree.Tree;
import gov.vha.isaac.ochre.api.tree.TreeNodeVisitData;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeWithBitSets;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.api.MultiException;
import org.ihtsdo.otf.tcc.api.contradiction.ContradictionException;
import org.ihtsdo.otf.tcc.api.coordinate.ViewCoordinate;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;
import org.jvnet.testing.hk2testng.HK2;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author kec
 */
// https://www.jfokus.se/jfokus08/pres/jf08-HundredKilobytesKernelHK2.pdf
// https://github.com/saden1/hk2-testng
@HK2("cradle")
public class CradleIntegrationTests {

    public static void main(String[] args) {
        try {
            CradleIntegrationTests cit = new CradleIntegrationTests();
            cit.setUpSuite();
            cit.testLoad();
            cit.tearDownSuite();
        } catch (Exception ex) {
            log.fatal(ex.getLocalizedMessage(), ex);
        }
        System.exit(0);
    }

    private static final Logger log = LogManager.getLogger();
    private boolean dbExists = false;
    private static IdentifierService sequenceProvider;
    private static ConceptService conceptService;

    @BeforeGroups(groups = {"db"})
    public void setUpSuite() throws Exception {
        log.info("oneTimeSetUp");

        System.setProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY, "target/object-chronicles");

        java.nio.file.Path dbFolderPath = Paths.get(System.getProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY));
        dbExists = dbFolderPath.toFile().exists();
        System.out.println("termstore folder path: " + dbFolderPath.toFile().exists());

        LookupService.getService(ConfigurationService.class).setConceptModel(ConceptModel.OCHRE_CONCEPT_MODEL);
        LookupService.startupIsaac();
        sequenceProvider = LookupService.getService(IdentifierService.class);
        conceptService = LookupService.getService(ConceptService.class);
        ActiveTasksTicker.start(10);
        HeapUseTicker.start(10);
    }

    @AfterGroups(groups = {"db"})
    public void tearDownSuite() throws Exception {
        log.info("oneTimeTearDown");
        LookupService.shutdownIsaac();
        ActiveTasksTicker.stop();
        HeapUseTicker.stop();
    }

    @BeforeMethod
    public void setUp() throws Exception {

    }

    @AfterMethod
    public void tearDown() throws Exception {

    }

    @Test(groups = {"db"})
    public void testLoad() throws Exception {

        log.info("  Testing load...");
        ObjectChronicleTaskService tts = LookupService.get().getService(ObjectChronicleTaskService.class);

        String mapDbFolder = System.getProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY);
        if (mapDbFolder == null || mapDbFolder.isEmpty()) {
            throw new IllegalStateException(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY + " has not been set.");
        }

        if (!dbExists) {
            loadDatabase(tts);
            boolean differences = testLoad(tts);

            /*
             There will be some differences as importing change sets or the 
             path eConcept file may have multiple incremental entries for 
             a single concept. 
            
             May want a white list or similar at some point.
             Assert.assertTrue(differences);
             */
        }
        testTaxonomy();

        walkTaxonomy();

        findRoots();

        HashTreeWithBitSets g = makeGraph();
        log.info("    taxonomy graph size:" + g.size());
        log.info("    taxonomy graph roots: " + Arrays.toString(g.getRootSequences()));
        for (int rootSequence : IntStream.of(g.getRootSequences()).limit(100).toArray()) {
            log.info("    rootSequence: " + rootSequence);
            ConceptChronology aRoot = conceptService.getConcept(rootSequence);
            log.info("    root concept: " + aRoot.toUserString());
            log.info("    parents of root" + Arrays.toString(g.getParentSequences(rootSequence)));
            TreeNodeVisitData dfsData = g.depthFirstProcess(rootSequence,
                    (nodeVisitData, sequence) -> {
                    });

            int maxDepth = dfsData.getMaxDepth();
            log.info(" Max depth from root (dfs): " + maxDepth);
            TreeNodeVisitData bfsData = g.breadthFirstProcess(rootSequence,
                    (nodeVisitData, sequence) -> {
                    });

            int maxBfsDepth = bfsData.getMaxDepth();
            log.info(" Max depth from root (bfs): " + maxBfsDepth);
            log.info("\n\n");
        }

        log.info("  Taxonomy graph leaves: " + g.getLeafSequences().count());
        log.info("  Taxonomy graph size: " + g.size());
        log.info("  Taxonomy graph concepts with parents count: " + g.conceptSequencesWithParentsCount());
        log.info("  Taxonomy graph concepts with children count: " + g.conceptSequencesWithChildrenCount());

        StringBuilder sb = new StringBuilder();
        ConceptChronology root = conceptService.getConcept(IsaacMetadataAuxiliaryBinding.ISAAC_ROOT.getNid());
        TaxonomyService taxonomyService = LookupService.getService(TaxonomyService.class);

        int[] originSequences = taxonomyService.getAllRelationshipOriginSequences(root.getConceptSequence()).toArray();
        AtomicInteger relCount = new AtomicInteger(1);
        if (originSequences.length == 0) {
            log.info(" No incoming rels for: " + root);
        } else {
            log.info(" Found " + originSequences.length + " incoming rels for: " + root);
            for (int originSequence : originSequences) {
                sb.append(relCount.getAndIncrement()).append(": ").append(conceptService.getConcept(originSequence).toUserString()).append("\n");
            }
        }
        log.info(sb.toString());
        //
    }

    private void findRoots() {
        try {
            ViewCoordinate vc = ViewCoordinates.getDevelopmentInferredLatestActiveOnly();
            log.info("Walking 10 concepts to root inferred.");
            IntStream conceptSequenceStream = sequenceProvider.getConceptSequenceStream().
                    filter((int conceptSequence) -> conceptService.isConceptActive(conceptSequence,
                                    vc)).limit(10);
            TaxonomyService taxonomyService = LookupService.getService(TaxonomyService.class);
            conceptSequenceStream.forEach((int conceptSequence) -> {
                System.out.println("Concept sequence: " + conceptSequence);
                walkToRoot(conceptSequence, taxonomyService, vc, 0, new BitSet());
                System.out.println("\n\n");
            });
            log.info("Walking 10 concepts to root stated.");
            conceptSequenceStream = sequenceProvider.getConceptSequenceStream().
                    filter((int conceptSequence) -> conceptService.isConceptActive(conceptSequence,
                                    vc)).limit(10);
            ViewCoordinate vc2 = ViewCoordinates.getDevelopmentStatedLatestActiveOnly();
            conceptSequenceStream.forEach((int conceptSequence) -> {
                System.out.println("Concept sequence: " + conceptSequence);
                walkToRoot(conceptSequence, taxonomyService, vc2, 0, new BitSet());
                System.out.println("\n\n");
            });
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    private void walkToRoot(int child, TaxonomyService taxonomyService, TaxonomyCoordinate tc,
            int depth, BitSet visited) {
        visited.set(child);
        if (depth > 50) {
            return;
        }

        if (conceptService.isConceptActive(child, tc.getStampCoordinate())) {
            printTaxonomyLevel(child, depth, "");
            taxonomyService.getTaxonomyParentSequences(child, tc).forEach((parentSequence) -> {
                if (!visited.get(parentSequence)) {
                    walkToRoot(parentSequence, taxonomyService, tc, depth + 1, visited);
                } else {
                    printTaxonomyLevel(parentSequence, depth + 1, "...");
                }
            });
       }
    }

    private void printTaxonomyLevel(int child, int depth, String suffix) {
        ConceptChronology childConcept = conceptService.getConcept(child);

        StringBuilder sb = new StringBuilder();
        sb.append(" ");
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(childConcept.toUserString());
        sb.append("; ");
        sb.append(childConcept.getNid());
        sb.append(":");
        sb.append(child);
        sb.append(suffix);
        System.out.println(sb.toString());
    }

    private void loadDatabase(ObjectChronicleTaskService tts) throws ExecutionException, IOException, MultiException, InterruptedException {
        Path snomedDataFile = Paths.get("target/data/sctSiEConcepts.jbin");
        Path isaacMetadataFile = Paths.get("target/data/isaac/metadata/econ/IsaacMetadataAuxiliary.econ");

        Instant start = Instant.now();

        Task<Integer> loadTask = tts.startLoadTask(IsaacMetadataAuxiliaryBinding.DEVELOPMENT,
                isaacMetadataFile, snomedDataFile);
        int conceptCount = loadTask.get();
        Instant finish = Instant.now();
        Duration duration = Duration.between(start, finish);
        log.info("  Loaded " + conceptCount + " concepts in: " + duration);
        double nsPerConcept = 1.0d * duration.toNanos() / conceptCount;
        log.info("  nsPerConcept: {}", nsPerConcept);

        double msPerConcept = 1.0d * duration.toMillis() / conceptCount;
        log.info("  msPerConcept: {}", msPerConcept);

        log.info("  concepts in map: {}", conceptService.getConceptCount());

        log.info("  sequences map: {}", sequenceProvider.getConceptSequenceStream().distinct().count());
    }

    private boolean testLoad(ObjectChronicleTaskService tts) throws ExecutionException, IOException, MultiException, InterruptedException {
        Path snomedDataFile = Paths.get("target/data/sctSiEConcepts.jbin");
        Path isaacMetadataFile = Paths.get("target/data/isaac/metadata/econ/IsaacMetadataAuxiliary.econ");
        Instant start = Instant.now();

        Task<Boolean> verifyTask = tts.startVerifyTask(IsaacMetadataAuxiliaryBinding.DEVELOPMENT,
                isaacMetadataFile, snomedDataFile);
        boolean verified = verifyTask.get();
        Instant finish = Instant.now();
        Duration duration = Duration.between(start, finish);
        log.info("  Verified concepts in: " + duration);
        log.info("  concepts in map: {}", conceptService.getConceptCount());
        log.info("  sequences map: {}", sequenceProvider.getConceptSequenceStream().distinct().count());

        return verified;
    }

    private void testTaxonomy() {
        try {
            testTaxonomy(ViewCoordinates.getDevelopmentInferredLatest());
            testTaxonomy(ViewCoordinates.getDevelopmentStatedLatest());
        } catch (IOException | ContradictionException ex) {
            log.error(ex.getLocalizedMessage(), ex);
        }
    }

    private void testTaxonomy(ViewCoordinate vc) throws IOException, ContradictionException {
        int disorderOfCorneaNid = Snomed.DISORDER_OF_CORNEA.getNid();
        int disorderOfEyeNid = Snomed.DISORDER_OF_EYE.getNid();
        TaxonomyService taxonomyService = LookupService.getService(TaxonomyService.class);

        boolean isChild = taxonomyService.isChildOf(disorderOfCorneaNid, disorderOfEyeNid, vc);
        boolean isKind = taxonomyService.isKindOf(disorderOfCorneaNid, disorderOfEyeNid, vc);

        System.out.println("Cornea is a " + vc.getRelationshipAssertionType() + " child-of disorder of eye: " + isChild);
        System.out.println("Cornea is a " + vc.getRelationshipAssertionType() + " kind-of of disorder of eye: " + isKind);
    }

    private void walkTaxonomy() throws IOException {
        log.info("  Start walking taxonomy.");
        Instant collectStart = Instant.now();
        IntStream conceptSequenceStream = sequenceProvider.getParallelConceptSequenceStream();
        TaxonomyWalkCollector collector = new TaxonomyWalkCollector(
                ViewCoordinates.getDevelopmentStatedLatestActiveOnly());
        TaxonomyWalkAccumulator taxonomyWalkAccumulator = conceptSequenceStream.collect(
                TaxonomyWalkAccumulator::new,
                collector,
                collector);
        Instant collectEnd = Instant.now();
        Duration collectDuration = Duration.between(collectStart, collectEnd);
        log.info("  Finished walking taxonomy: " + taxonomyWalkAccumulator);
        log.info("  Collection duration: " + collectDuration);
    }

    private HashTreeWithBitSets makeGraph() throws IOException {
        log.info("  Start to make taxonomy snapshot graph.");
        Instant collectStart = Instant.now();
        TaxonomyService taxonomyService = LookupService.getService(TaxonomyService.class);
        Tree taxonomyTree = taxonomyService.getTaxonomyTree(ViewCoordinates.getDevelopmentInferredLatestActiveOnly());
        Instant collectEnd = Instant.now();
        Duration collectDuration = Duration.between(collectStart, collectEnd);
        log.info("  Finished making graph: " + taxonomyTree);
        log.info("  Generation duration: " + collectDuration);
        return (HashTreeWithBitSets) taxonomyTree;
    }

}