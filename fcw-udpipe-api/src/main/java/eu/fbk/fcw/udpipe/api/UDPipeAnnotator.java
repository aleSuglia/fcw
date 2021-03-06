package eu.fbk.fcw.udpipe.api;

import com.google.gson.Gson;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import eu.fbk.fcw.utils.ConllToken;
import eu.fbk.fcw.utils.Network;
import eu.fbk.utils.core.PropertiesUtils;
import eu.fbk.utils.corenlp.CustomAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by alessio on 07/02/17.
 */

public class UDPipeAnnotator implements Annotator {

    private static final Logger LOGGER = LoggerFactory.getLogger(UDPipeAnnotator.class);

    private static String DEFAULT_SERVER = "localhost";
    private static String DEFAULT_PORT = "12345";
    private static String DEFAULT_ADDRESS = "/process";
    private static String DEFAULT_PROTOCOL = "http";

    private URL url;
    private boolean alreadyTokenized;
    private boolean keepOriginal;

    public UDPipeAnnotator(String annotatorName, Properties properties) throws MalformedURLException {
        Properties theseProperties = PropertiesUtils.dotConvertedProperties(properties, annotatorName);

        String server = theseProperties.getProperty("server", DEFAULT_SERVER);
        String port = theseProperties.getProperty("port", DEFAULT_PORT);
        String protocol = theseProperties.getProperty("protocol", DEFAULT_PROTOCOL);
        String address = theseProperties.getProperty("address", DEFAULT_ADDRESS);

        alreadyTokenized = PropertiesUtils.getBoolean(theseProperties.getProperty("alreadyTokenized"), true);
        keepOriginal = PropertiesUtils.getBoolean(theseProperties.getProperty("keepOriginal"), false);

        url = new URL(protocol, server, Integer.parseInt(port), address);
    }

    @Override public void annotate(Annotation annotation) {
        Map<String, String> data = new HashMap<>();
        data.put("tagger", "1");
        data.put("parser", "1");
        String dataStr = annotation.get(CoreAnnotations.TextAnnotation.class);
        if (alreadyTokenized) {
            data.put("input", "horizontal");
            StringBuffer buffer = new StringBuffer();
            for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
                StringBuffer line = new StringBuffer();
                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    line.append(token.originalText().replaceAll("\\s+", "_")).append(" ");
                }
                buffer.append(line.toString().trim()).append("\n");
            }
            dataStr = buffer.toString().trim();
        } else {
            data.put("tokenizer", "1");
        }
        data.put("data", dataStr);

        try {
            String response = Network.request(url.toString(), data);
            Gson gson = new Gson();
            UDPipe udPipe = gson.fromJson(response, UDPipe.class);

            String result = udPipe.getResult();

            if (keepOriginal) {
                annotation.set(UDPipeAnnotations.UDPipeOriginalAnnotation.class, result);
            }

            List<List<ConllToken>> text = new ArrayList<>();
            List<ConllToken> thisSentence = new ArrayList<>();

            BufferedReader reader;

            reader = new BufferedReader(new StringReader(result));
            String line;
            int sentenceOffset = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) {
                    if (thisSentence.size() > 0) {
                        text.add(thisSentence);
                        sentenceOffset += thisSentence.size();
                        thisSentence = new ArrayList<>();
                    }
                    continue;
                }

                try {
                    ConllToken token = new ConllToken(line, sentenceOffset);
//                    System.out.println("Adding token " + token.toString() + " with offset " + sentenceOffset);
                    thisSentence.add(token);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (thisSentence.size() > 0) {
                text.add(thisSentence);
            }
            reader.close();

            StringBuffer res;

            res = new StringBuffer();
            res.append(result);

            res = new StringBuffer();
            for (List<ConllToken> sentence : text) {
                for (ConllToken token : sentence) {
                    res.append(token.getId()).append("\t");
                    res.append(token.getOriginalParts()[1]).append("\t");
                    res.append(token.getOriginalParts()[2]).append("\t");
                    res.append(token.getOriginalParts()[3]).append("\t");
                    res.append(token.getOriginalParts()[4]).append("\t");
                    res.append(token.getOriginalParts()[5]).append("\t");
                    res.append(nn(token.getHead().toString())).append("\t");
                    res.append(token.getOriginalParts()[7]).append("\t");
                    res.append("_").append("\t");
                    res.append(token.getOriginalParts()[9]).append("\n");
                }

                res.append("\n");
            }

//            System.out.println(res.toString());

            List<SemanticGraph> graphs = new ArrayList<>();
            reader = new BufferedReader(new StringReader(res.toString()));
            CoNLLUDocumentReader depReader = new CoNLLUDocumentReader();
            Iterator<SemanticGraph> it = depReader.getIterator(reader);
            it.forEachRemaining(graphs::add);
            reader.close();

            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
//            int sentenceIndex = 0;
            for (int i = 0; i < sentences.size(); i++) {
                CoreMap sentence = sentences.get(i);

                try {
                    SemanticGraph semanticGraph = graphs.get(i);
//                    for (int j = 0; j < semanticGraph.size(); j++) {
//                        IndexedWord node = semanticGraph.getNodeByIndex(j);
//                        node.setIndex(node.index() + sentenceIndex);
//                    }
//                    for (IndexedWord indexedWord : semanticGraph.getRoots()) {
//                        System.out.println(indexedWord.index());
//                    }
                    sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, semanticGraph);
                } catch (Exception e) {
                    LOGGER.warn(e.getMessage());
                }

                List<CoreLabel> get1 = sentence.get(CoreAnnotations.TokensAnnotation.class);
                for (int j = 0; j < get1.size(); j++) {
                    CoreLabel token = get1.get(j);
                    ConllToken conllToken = text.get(i).get(j);
//                    System.out.println(conllToken.getFeats());
//                    System.out.println(token);

                    token.set(CoreAnnotations.PartOfSpeechAnnotation.class, conllToken.getXpos());
                    token.set(CoreAnnotations.LemmaAnnotation.class, conllToken.getLemma());
                    token.set(CustomAnnotations.UPosAnnotation.class, conllToken.getUpos());
                    token.set(CustomAnnotations.FeaturesAnnotation.class, conllToken.getFeats());
                    token.set(CustomAnnotations.DepsAnnotation.class, conllToken.getDeps());
                    token.set(UDPipeAnnotations.LemmaAnnotation.class, conllToken.getLemma());
                    token.set(CustomAnnotations.MiscAnnotation.class, conllToken.getMisc());
                }

//                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
//                    System.out.println(token.get(UDPipeAnnotations.FeaturesAnnotation.class));
//                }
//                System.out.println();

//                sentenceIndex += sentence.size();
            }

//            for (List<ConllToken> sentence : text) {
//                System.out.println("SENTENCE");
//                for (ConllToken token : sentence) {
//                    System.out.println(token);
//                }
//            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String nn(String value) {
        return value == null ? "_" : value;
    }

    @Override public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
        return new HashSet<>(Arrays.asList(
                CoreAnnotations.TextAnnotation.class,
                CoreAnnotations.TokensAnnotation.class,
                CoreAnnotations.CharacterOffsetBeginAnnotation.class,
                CoreAnnotations.CharacterOffsetEndAnnotation.class,
                CoreAnnotations.BeforeAnnotation.class,
                CoreAnnotations.AfterAnnotation.class,
                CoreAnnotations.TokenBeginAnnotation.class,
                CoreAnnotations.TokenEndAnnotation.class,
                CoreAnnotations.PositionAnnotation.class,
                CoreAnnotations.IndexAnnotation.class,
                CoreAnnotations.OriginalTextAnnotation.class,
                CoreAnnotations.ValueAnnotation.class,
                CoreAnnotations.PartOfSpeechAnnotation.class,
                CustomAnnotations.UPosAnnotation.class,
                CustomAnnotations.FeaturesAnnotation.class,
                CustomAnnotations.DepsAnnotation.class,
                UDPipeAnnotations.LemmaAnnotation.class,
                CoreAnnotations.LemmaAnnotation.class,
                CustomAnnotations.MiscAnnotation.class,
                SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class
        ));
    }

    @Override public Set<Class<? extends CoreAnnotation>> requires() {
        if (alreadyTokenized) {
            return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
                    CoreAnnotations.TextAnnotation.class,
                    CoreAnnotations.TokensAnnotation.class,
                    CoreAnnotations.CharacterOffsetBeginAnnotation.class,
                    CoreAnnotations.CharacterOffsetEndAnnotation.class,
                    CoreAnnotations.SentencesAnnotation.class
            )));
        }
        return Collections.emptySet();
    }

}
