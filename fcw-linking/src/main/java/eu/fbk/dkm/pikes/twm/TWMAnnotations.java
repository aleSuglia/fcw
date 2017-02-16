package eu.fbk.dkm.pikes.twm;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.ErasureUtils;
import eu.fbk.utils.gson.JSONLabel;

import java.util.List;

/**
 * Created by alessio on 22/09/16.
 */

public class TWMAnnotations {

    @JSONLabel("linking")
    public static class DBpediaSpotlightAnnotation implements CoreAnnotation<LinkingTag> {

        @Override public Class<LinkingTag> getType() {
            return LinkingTag.class;
        }
    }

    @JSONLabel("linkings")
    public static class LinkingAnnotations implements CoreAnnotation<List<LinkingTag>> {

        @Override public Class<List<LinkingTag>> getType() {
            return ErasureUtils.<Class<List<LinkingTag>>>uncheckedCast(List.class);
        }
    }

}
