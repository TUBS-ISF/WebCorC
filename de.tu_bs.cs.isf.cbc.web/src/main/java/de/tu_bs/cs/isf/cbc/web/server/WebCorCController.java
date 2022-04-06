package de.tu_bs.cs.isf.cbc.web.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import de.tu_bs.cs.isf.cbc.cbcmodel.CbCFormula;
import de.tu_bs.cs.isf.cbc.cbcmodel.CbcmodelFactory;
import de.tu_bs.cs.isf.cbc.cbcmodel.CbcmodelPackage;
import de.tu_bs.cs.isf.cbc.cbcmodel.Condition;
import de.tu_bs.cs.isf.cbc.cbcmodel.GlobalConditions;
import de.tu_bs.cs.isf.cbc.cbcmodel.JavaVariable;
import de.tu_bs.cs.isf.cbc.cbcmodel.JavaVariables;
import de.tu_bs.cs.isf.cbc.web.java.compilation.WebCorcCompileJava;
import de.tu_bs.cs.isf.cbc.web.util.JSONBuildHelper;
import de.tu_bs.cs.isf.cbc.web.util.JSONParser;
import de.tu_bs.cs.isf.cbc.web.util.ProveWithKey;
import de.tu_bs.cs.isf.cbc.web.util.VerifyAllStatements;

@RestController
public class WebCorCController {
	// TODO: check via session.getLastAccess if client has no folder
	// TODO: new session id is given to client - after refresh the old session id is
	// claimed again... not the desired behavior

	// continue using this directory path
//	private final String SZ_LOCATION = System.getProperty("java.io.tmpdir") + "WebCorC";
	private final String SZ_LOCATION = "C:\\Users\\Malena\\Desktop\\WebCorCTemp";

	@GetMapping(value = "/sessionId")
	public String getSessionId(HttpSession session) {
		// session ids should never be invalidated (maybe an other place for this?)
		session.setMaxInactiveInterval(0);
		System.out.println(SZ_LOCATION);
		return session.getId();
	}

	@RequestMapping(value = "/saveFile", method = RequestMethod.POST)
	public void saveFileToDisk(@RequestBody String fileAndContent, HttpSession session) throws IOException {
		// TODO: if the file is not existing: create one
		JSONObject jObj = new JSONObject(fileAndContent);
		
		System.out.println(fileAndContent);

		String pathString = JSONParser.getPathString(jObj, session);
		String nameString = JSONParser.getNameString(jObj);
		String content = JSONParser.getContentString(jObj);
		String systemPath = SZ_LOCATION + File.separator + pathString + File.separator + nameString;

		Writer writer = new FileWriter(systemPath, false);
		writer.write(content);
		writer.close();

//		Files.writeString(path, content, StandardOpenOption.APPEND );

		System.out.println("File saved");

	}

	@RequestMapping(value = "/createFile", method = RequestMethod.POST)
	public String createNewFile(@RequestBody String fileAndContent, HttpSession session) {
		// file content is most likely an empty string. May be different with other usages in the future.
		JSONObject jObj = new JSONObject(fileAndContent);

		String pathString = JSONParser.getPathString(jObj, session);
		String nameString = JSONParser.getNameString(jObj);
		String systemPath = SZ_LOCATION + File.separator + pathString + File.separator + nameString;

		File newFile = new File(systemPath);
		try {
			if (newFile.createNewFile()) {
				System.out.println("File created: " + newFile.getName());
				return "File " + newFile.getName() + " created successfully";
			} else {
				System.out.println("File already exists.");
				return "File " + newFile.getName() + " already exists";
			}
		} catch (IOException e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}

	@RequestMapping(value = "/getFileAtPath", method = RequestMethod.POST)
	public String deliverFileAtPath(@RequestBody String pathString, HttpSession session) throws IOException {
		String[] pathParts = pathString.split("/");
		pathString = String.join(File.separator, pathParts);
		Path path = Path.of(SZ_LOCATION + File.separator + session.getId() + File.separator + pathString);
		System.out.println(path);

		return Files.readString(path);
	}

	@RequestMapping(value = "/initialize", method = RequestMethod.POST)
	public String initializeWorkspace(HttpSession session) {
//		if (!session.getId().equals(sessionId)) {
//			System.out.println("Session Id proplem occured! At this point of implementation, this should not be the case!");
//		}

		// check if there is an existing folder named sessionId. If not - create new
		// "web workspace"
		JSONObject webDirectory = new JSONObject();
		if (new File(SZ_LOCATION + "/" + session.getId()).exists()) {
			System.out.println("Folder found for SessionId: " + session.getId());

		} else {
			System.out.println("No existing folder... Creating new directory! For SessionId: " + session.getId());
			// directory : folder named after sessionId -> web directory tree, .meta,
			// proofData
			File newRootDir = new File(SZ_LOCATION + File.separator + session.getId());
			File newSubDir = new File(SZ_LOCATION + File.separator + session.getId() + File.separator + "WebDirectory");
			File newSubDirMeta = new File(SZ_LOCATION + File.separator + session.getId() + File.separator + ".meta");
			File newSubDirProof = new File(
					SZ_LOCATION + File.separator + session.getId() + File.separator + "ProofData");
			File newSubDirHelper = new File(
					SZ_LOCATION + File.separator + session.getId() + File.separator + "HelperFiles");
			newRootDir.mkdir();
			newSubDir.mkdir();
			newSubDirMeta.mkdir();
			newSubDirProof.mkdir();
			newSubDirHelper.mkdir();

		}
		webDirectory.put("directory",
				JSONBuildHelper.buildDirectoryJSON(SZ_LOCATION + File.separator + session.getId()));
		return webDirectory.toString();
	}

	@RequestMapping(value = "/javaCodeAsString", method = RequestMethod.POST, consumes = "text/plain")
	public String javaCodeComp(@RequestBody String javaCodeBlock) {
		WebCorcCompileJava javaCompiler = new WebCorcCompileJava();
		// TODO: compile message in JSON format: like message with message-string and
		// also other useful information like the line separated
		return javaCompiler.compileJavaString(javaCodeBlock);
	}

	@RequestMapping(value = "/helperFileUpload", method = RequestMethod.POST)
	public String uploadHelperFile(@RequestParam("file") MultipartFile file, HttpSession session) {
		String szSessionId = session.getId();
		File keyHelperFile = new File(URI
				.createFileURI(SZ_LOCATION + "/" + "Proofs" + "/" + szSessionId + "/" + "helper.key").toFileString());
		try {
			keyHelperFile.getParentFile().mkdirs();
			file.transferTo(keyHelperFile);
		} catch (IllegalStateException e) {
			e.printStackTrace();
			return "Upload of file failed: " + e.getMessage();
		} catch (IOException e) {
			e.printStackTrace();
			return "Upload of file failed: " + e.getMessage();
		}

		return "Upload of file successful";
	}

	@RequestMapping(value = "/javaFileUpload", method = RequestMethod.POST)
	public String uploadJavaFile(@RequestParam("file") MultipartFile file, HttpSession session) {
		String szSessionId = session.getId();
		File keyJavaFile = new File(
				URI.createFileURI(SZ_LOCATION + "/" + "Proofs" + "/" + szSessionId + "/Java/" + "UserCode.java")
						.toFileString());
		File keyJavaFileSnapshot = new File(URI.createFileURI(SZ_LOCATION + "/" + "Proofs" + "/" + szSessionId
				+ "/JavaSnapshots/" + String.valueOf(System.currentTimeMillis()) + "_UserCode.java").toFileString());
		try {
			keyJavaFile.getParentFile().mkdirs();
			file.transferTo(keyJavaFile);
			keyJavaFileSnapshot.getParentFile().mkdirs();
			Files.copy(keyJavaFile.toPath(), keyJavaFileSnapshot.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IllegalStateException e) {
			e.printStackTrace();
			return "Upload of file failed: " + e.getMessage();
		} catch (IOException e) {
			e.printStackTrace();
			return "Upload of file failed: " + e.getMessage();
		}

		boolean result = ProveWithKey.proveJavaWithKey(keyJavaFile);
		return "Proof is closed: " + result;
	}

	@RequestMapping(value = "/verifyAll", method = RequestMethod.POST, consumes = "application/json")
	public String processJson(@RequestBody String requestPayload, HttpSession session) {
		String szSessionId = session.getId();
		// Parsing the JSON-tree and create the EObject with the CbcmodelFactory,
		// unescaping necessary of HTML entities
		JSONObject jObjTree = new JSONObject(HtmlUtils.htmlUnescape(requestPayload));
		jObjTree = jObjTree.getJSONObject("CorcInput");
		// Initializations and registrations

		// Ecore Magie
		{
			// Ecore aufwecken / initialisieren!?
			CbcmodelPackage.eINSTANCE.eClass();
			// Ecore file extension registry holen und .cbcmode erweiterung mit XMI
			// resources mappen
			Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
			Map<String, Object> m = reg.getExtensionToFactoryMap();
			m.put("cbcmodel", new XMIResourceFactoryImpl());
		}

		// ressourcen liste um ressourcen zu erstellen
		ResourceSet rs = new ResourceSetImpl();

		// Create resource & model instance
		// name einfach um das von der nächsten verifikation zu unterscheiden
		String szPathName = System.currentTimeMillis() + "_" + jObjTree.getString("name").replace(" ", "")
				+ ".cbcmodel";
		// ressource mithilfe vom ressource set erstellen --> vielleicht nicht jedes mal
		// ein neues set?
		Resource rResource = rs
				.createResource(URI.createFileURI(SZ_LOCATION + "/" + "Proofs" + "/" + szSessionId + "/" + szPathName));

		// add variables and global conditions
		JavaVariables jvVars = CbcmodelFactory.eINSTANCE.createJavaVariables();

		JSONArray jArrVariables = jObjTree.getJSONArray("javaVariables");
		for (int i = 0; i < jArrVariables.length(); i++) {
			JavaVariable jvVar = CbcmodelFactory.eINSTANCE.createJavaVariable();
			jvVar.setName(jArrVariables.getString(i));
			jvVars.getVariables().add(jvVar);
		}

		// hier das gleiche
		GlobalConditions gcConditions = CbcmodelFactory.eINSTANCE.createGlobalConditions();
		JSONArray jArrGlobals = jObjTree.getJSONArray("globalConditions");
		for (int i = 0; i < jArrGlobals.length(); i++) {
			Condition gc = CbcmodelFactory.eINSTANCE.createCondition();
			gc.setName(jArrGlobals.getString(i));
			gcConditions.getConditions().add(gc);
		}

		rResource.getContents().add(jvVars);
		rResource.getContents().add(gcConditions);

		// initiate recursive parsing, also adding the formula to the rResource
		JSONParser.parseFormulaTree(jObjTree, rResource, null);

		try {
			rResource.save(Collections.EMPTY_MAP);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Verify model, result be written into the resource
		VerifyAllStatements.verify(rResource);
		szPathName = System.currentTimeMillis() + "_" + jObjTree.getString("name").replace(" ", "")
				+ "_evaluated.cbcmodel";
		rResource.setURI(URI.createFileURI(SZ_LOCATION + "/" + "Proofs" + "/" + szSessionId + "/" + szPathName));

		try {
			rResource.save(Collections.EMPTY_MAP);
		} catch (IOException e) {
			e.printStackTrace();
		}

		CbCFormula formula = null;
		for (EObject eObj : rResource.getContents()) {
			if (eObj instanceof CbCFormula) {
				formula = (CbCFormula) eObj;
			}
		}

		JSONParser.createJSONResponse(jObjTree, formula.getStatement());
		JSONObject jObjResponse = new JSONObject();
		jObjResponse.put("sessionId", szSessionId);
		jObjResponse.put("CorcOutput", jObjTree);

		return jObjResponse.toString();
	}
}