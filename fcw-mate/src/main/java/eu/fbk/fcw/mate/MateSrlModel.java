package eu.fbk.fcw.mate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.lth.cs.srl.SemanticRoleLabeler;
import se.lth.cs.srl.languages.Language;
import se.lth.cs.srl.pipeline.Pipeline;

import java.io.File;
import java.util.zip.ZipFile;

/**
 * Created by alessio on 25/05/15.
 */

public class MateSrlModel {

	private static MateSrlModel instance;
	private SemanticRoleLabeler labeler;
	private static final Logger LOGGER = LoggerFactory.getLogger(MateSrlModel.class);

	private MateSrlModel(File mateModel) {
		LOGGER.info("Loading model for Mate Tools");

		try {
			ZipFile zipFile;
			zipFile = new ZipFile(mateModel);
			labeler = Pipeline.fromZipFile(zipFile);
			zipFile.close();
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}

		Language.setLanguage(Language.L.valueOf("eng"));
	}

	public static MateSrlModel getInstance(File posModel) {
		if (instance == null) {
			instance = new MateSrlModel(posModel);
		}

		return instance;
	}

	public SemanticRoleLabeler getLabeler() {
		return labeler;
	}
}
