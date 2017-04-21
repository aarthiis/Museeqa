package de.citec.sc.main;

import de.citec.sc.corpus.AnnotatedDocument;
import de.citec.sc.corpus.QALDCorpus;
import de.citec.sc.dudes.rdf.ExpressionFactory;
import de.citec.sc.dudes.rdf.RDFDUDES;
import de.citec.sc.learning.QueryConstructor;
import static de.citec.sc.main.Pipeline.nelTemplates;
import static de.citec.sc.main.Pipeline.scorer;
import de.citec.sc.qald.QALDCorpusLoader;
import de.citec.sc.query.CandidateRetriever;
import de.citec.sc.query.CandidateRetrieverOnLucene;
import de.citec.sc.query.CandidateRetrieverOnMemory;
import de.citec.sc.query.ManualLexicon;
import de.citec.sc.query.Search;
import de.citec.sc.template.QATemplateFactory;
import de.citec.sc.utils.DBpediaEndpoint;
import de.citec.sc.utils.ProjectConfiguration;
import de.citec.sc.variable.State;
import de.citec.sc.wordNet.WordNetAnalyzer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import learning.Model;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger log = LogManager.getFormatterLogger();

    public static void main(String[] args) {

        if (args.length > 0) {

        } else {

            args = new String[26];
            args[0] = "-d1";//query dataset
            args[1] = "qald6Train";//qald6Train  qald6Test   qaldSubset
            args[2] = "-d2";  //test dataset
            args[3] = "qald6Test";//qald6Train  qald6Test   qaldSubset
            args[4] = "-m1";//manual lexicon
            args[5] = "true";//true, false
            args[6] = "-m2";//matoll
            args[7] = "true";//true, false
            args[8] = "-e";//epochs
            args[9] = "" + 1;
            args[10] = "-s";//sampling steps
            args[11] = "" + 15;
            args[12] = "-k1";//top k samples to select from during training NEL
            args[13] = "" + 4;
            args[14] = "-k2";//top k samples to select from during training for QA
            args[15] = "" + 4;
            args[16] = "-l1";//top k samples to select from during testing for NEL
            args[17] = "" + 10;
            args[18] = "-l2";//top k samples to select from during testing for QA
            args[19] = "" + 10;
            args[20] = "-w1";//max word count - train
            args[21] = "" + 30;
            args[22] = "-w2";//max word count - test
            args[23] = "" + 30;
            args[24] = "-i";//index
            args[25] = "lucene";//lucene, memory
        }

        ProjectConfiguration.loadConfigurations(args);

        log.info(ProjectConfiguration.getAllParameters());
        
        System.out.println(ProjectConfiguration.getAllParameters());

        //load index, initialize postag lists etc.        
        initialize();

        //load training and testing corpus
        List<AnnotatedDocument> trainDocuments = getDocuments(QALDCorpusLoader.Dataset.valueOf(ProjectConfiguration.getTrainingDatasetName()), ProjectConfiguration.getTrainMaxWordCount());
        List<AnnotatedDocument> testDocuments = getDocuments(QALDCorpusLoader.Dataset.valueOf(ProjectConfiguration.getTestDatasetName()), ProjectConfiguration.getTestMaxWordCount());

        System.out.println("Training on " + ProjectConfiguration.getTrainingDatasetName() + " with " + trainDocuments.size());
        System.out.println("Testing on " + ProjectConfiguration.getTestDatasetName() + " with " + testDocuments.size());

        boolean trainOnly = true;
        if (trainOnly) {
            //train and test model
            try {
                List<Model<AnnotatedDocument, State>> trainedModels = Pipeline.train(trainDocuments);

                for (Model<AnnotatedDocument, State> m1 : trainedModels) {
                    if (trainedModels.indexOf(m1) == 0) {
                        m1.saveModelToFile("models", "model_nel_" + ProjectConfiguration.getTrainMaxWordCount() + "_" + ProjectConfiguration.getNumberOfEpochs());
                    }
                    if (trainedModels.indexOf(m1) == 1) {
                        m1.saveModelToFile("models", "model_qa_" + ProjectConfiguration.getTrainMaxWordCount() + "_" + ProjectConfiguration.getNumberOfEpochs());
                    }
                }

//                Pipeline.test(trainedModels, testDocuments);
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            //train and test model
            try {
                List<Model<AnnotatedDocument, State>> trainedModels = new ArrayList<>();

                QATemplateFactory factory = new QATemplateFactory();

                Model<AnnotatedDocument, State> modelNEL = new Model<>(Pipeline.scorer, Pipeline.nelTemplates);
                Model<AnnotatedDocument, State> modelQA = new Model<>(Pipeline.scorer, Pipeline.qaTemplates);

                modelNEL.loadModelFromDir("models/model_nel_" + ProjectConfiguration.getTrainMaxWordCount() + "_" + ProjectConfiguration.getNumberOfEpochs(), factory);
                modelQA.loadModelFromDir("models/model_qa_" + ProjectConfiguration.getTrainMaxWordCount() + "_" + ProjectConfiguration.getNumberOfEpochs(), factory);

                trainedModels.add(modelNEL);
                trainedModels.add(modelQA);

                Pipeline.test(trainedModels, testDocuments);
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    private static void initialize() {

        System.out.println("Initialization process has started ....");

        CandidateRetriever retriever = null;

        if (ProjectConfiguration.getIndex().equals("lucene")) {
            retriever = new CandidateRetrieverOnLucene(true, "luceneIndexes/resourceIndex", "luceneIndexes/classIndex", "luceneIndexes/predicateIndex", "luceneIndexes/matollIndex");
        } else {
            retriever = new CandidateRetrieverOnMemory("rawIndexFiles/resourceFiles", "rawIndexFiles/classFiles", "rawIndexFiles/predicateFiles", "rawIndexFiles/matollFiles", "rawIndexFiles/matollAdjectiveFiles");
        }

        WordNetAnalyzer wordNet = new WordNetAnalyzer("src/main/resources/WordNet-3.0/dict");

        Search.load(retriever, wordNet);
        Search.useMatoll(ProjectConfiguration.useMatoll());

        ManualLexicon.useManualLexicon(ProjectConfiguration.useManualLexicon());

        //semantic types to sample from
        Map<Integer, String> semanticTypes = new LinkedHashMap<>();
        semanticTypes.put(1, "Property");
        semanticTypes.put(2, "Individual");
        semanticTypes.put(3, "Class");
        semanticTypes.put(4, "RestrictionClass");

        //semantic types with special meaning
        Map<Integer, String> specialSemanticTypes = new LinkedHashMap<>();
        specialSemanticTypes.put(semanticTypes.size() + 1, "What");//it should be higher than semantic type size

        Set<String> validPOSTags = new HashSet<>();
        validPOSTags.add("NN");
        validPOSTags.add("NNP");
        validPOSTags.add("NNS");
        validPOSTags.add("NNPS");
        validPOSTags.add("VBZ");
        validPOSTags.add("VBN");
        validPOSTags.add("VBD");
        validPOSTags.add("VBG");
        validPOSTags.add("VBP");
        validPOSTags.add("VB");
        validPOSTags.add("JJ");

        Set<String> frequentWordsToExclude = new HashSet<>();
        //all of this words have a valid POSTAG , so they shouldn't be assigned any URI to these tokens
        frequentWordsToExclude.add("is");
        frequentWordsToExclude.add("was");
        frequentWordsToExclude.add("were");
        frequentWordsToExclude.add("are");
        frequentWordsToExclude.add("do");
        frequentWordsToExclude.add("does");
        frequentWordsToExclude.add("did");
        frequentWordsToExclude.add("give");
        frequentWordsToExclude.add("list");
        frequentWordsToExclude.add("show");
        frequentWordsToExclude.add("me");
        frequentWordsToExclude.add("many");
        frequentWordsToExclude.add("have");
        frequentWordsToExclude.add("belong");

        //these words can have special semantic type
        Set<String> wordsWithSpecialSemanticTypes = new HashSet<>();
        wordsWithSpecialSemanticTypes.add("which");
        wordsWithSpecialSemanticTypes.add("what");
        wordsWithSpecialSemanticTypes.add("who");
        wordsWithSpecialSemanticTypes.add("whom");
        wordsWithSpecialSemanticTypes.add("how");
        wordsWithSpecialSemanticTypes.add("when");
        wordsWithSpecialSemanticTypes.add("where");
        wordsWithSpecialSemanticTypes.add("give");
        wordsWithSpecialSemanticTypes.add("what");
        wordsWithSpecialSemanticTypes.add("show a list");

        Pipeline.initialize(validPOSTags, semanticTypes, specialSemanticTypes, frequentWordsToExclude, wordsWithSpecialSemanticTypes);

        QueryConstructor.initialize(specialSemanticTypes, semanticTypes, validPOSTags, frequentWordsToExclude, wordsWithSpecialSemanticTypes);

        System.out.println("Initialization process has ended ....");
    }

    private static List<AnnotatedDocument> getDocuments(QALDCorpusLoader.Dataset dataset, int maxWordCount) {

        boolean includeYAGO = false;
        boolean includeAggregation = false;
        boolean includeUNION = false;
        boolean onlyDBO = true;
        boolean isHybrid = false;

        QALDCorpus corpus = QALDCorpusLoader.load(dataset, includeYAGO, includeAggregation, includeUNION, onlyDBO, isHybrid);

        List<AnnotatedDocument> documents = new ArrayList<>();

        for (AnnotatedDocument d1 : corpus.getDocuments()) {

            if (DBpediaEndpoint.isValidQuery(d1.getGoldQueryString(), false)) {

                d1.getParse().mergeEdges();
                if (d1.getParse().getNodes().size() <= maxWordCount) {
                    documents.add(d1);
                }
            } else {
                System.out.println("Invalid query: " + d1.getQuestionString() + " Query: " + d1.getGoldQueryString().replace("\n", " "));
            }
        }
        System.out.print("Loaded dataset : " + dataset + " with " + documents.size() + " instances.");

        return documents;
    }
}