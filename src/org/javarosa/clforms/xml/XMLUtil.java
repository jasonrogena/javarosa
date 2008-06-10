/**
 * 
 */
package org.javarosa.clforms.xml;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.OutputConnection;
import javax.microedition.io.file.FileConnection;

import minixpath.XPathExpression;

import org.javarosa.clforms.api.Binding;
import org.javarosa.clforms.api.Constants;
import org.javarosa.clforms.api.Form;
import org.javarosa.clforms.api.Prompt;
import org.javarosa.clforms.storage.Model;
import org.javarosa.clforms.util.SimpleOrderedHashtable;
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.xmlpull.v1.XmlPullParser;

import org.javarosa.dtree.i18n.*;

import de.enough.polish.util.Locale;
import de.enough.polish.util.StringTokenizer;
import de.enough.polish.util.TextUtil;

/* droos: this code is getting a little out of hand; i think it needs to be restructured; it is too
 * spaghettified and there is too much special-casing and code duplication */

/**
 * 
 */
public class XMLUtil {
	/**
	 * Method to parse an XForm from an input stream.
	 * 
	 * @param inputStreamReader
	 * @return XForm
	 */
	public static Form parseForm(InputStreamReader isr) {
		Form f = new Form();
		parseForm(isr, f);
		return f;
	}
	
	/**
	 * Method to parse an input stream into an existing XForm.
	 * 
	 * @param inputStreamReader, XForm
	 * @return XForm
	 */
	public static void parseForm(InputStreamReader isr, Form form) {
		try {
			KXmlParser parser = new KXmlParser();
			parser.setInput(isr);
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
			Document doc = new Document();
			doc.parse(parser);
			
			Element html = doc.getRootElement();
			parseElement(form, html, null);
			
			//TODO FIGURE out why have to trim twice!
			form.getXmlModel().trimXML();
	        form.getXmlModel().trimXML();
			
	        if (form.getLocalizer() != null)
	        	form.getLocalizer().setToDefault();
		} catch (Exception ex) {
			System.out.println("XML PARSING ERROR");
			
			form = null;
			ex.printStackTrace();
		}
	}

	/**
	 * Recursive method to process XML.
	 * 
	 * @param xform
	 * @param element
	 * @param existingPrompt
	 * @return
	 */
	private static Prompt parseElement(Form form, Element element,
			Prompt existingPrompt) throws Exception{
		String label = ""; //$NON-NLS-1$
		String value = ""; //$NON-NLS-1$
        String textRef = "";
        
		int numOfEntries = element.getChildCount();
		for (int i = 0; i < numOfEntries; i++) {
			if (element.isText(i)) {
				// Text here are all insignificant white spaces.
				// We are only interested in children elements
			} else {
				Element child = element.getElement(i);
				String tagname = child.getName();
				if (TextUtil.equalsIgnoreCase(tagname,"head")) { //$NON-NLS-1$
					parseElement(form, child, null);
				} else if (TextUtil.equalsIgnoreCase(tagname,"body")) { //$NON-NLS-1$
					parseElement(form, child, null);
				} else if (TextUtil.equalsIgnoreCase(tagname,"title")) { //$NON-NLS-1$
					form.setName(getXMLText(child, 0, true));
				} else if (TextUtil.equalsIgnoreCase(tagname,"model")) { //$NON-NLS-1$
					Model model = new Model();
					Document xmlDoc = new Document();
					model.setXmlModel(xmlDoc);
					form.setXmlModel(model);
					for(int j=0;j<child.getAttributeCount();j++){
						if (TextUtil.equalsIgnoreCase(child.getAttributeName(j),"id")){
							form.setName(child.getAttributeValue(j));
							if (form.getXmlModel() != null)
								form.getXmlModel().setName(form.getName());
						}
					}
					parseElement(form, child, null);
				} else if (TextUtil.equalsIgnoreCase(tagname,"instance")) { //$NON-NLS-1$
					Document xmlDoc = form.getXmlModel().getXmlModel();
					xmlDoc.addChild(Node.ELEMENT, child.getElement(1));
				} else if (TextUtil.equalsIgnoreCase(tagname, "itext")) {
					parseIText(form, child);
				} else if (TextUtil.equalsIgnoreCase(tagname,"bind")) { //$NON-NLS-1$
					Binding b = new Binding();
					b.setId(child.getAttributeValue("", "id")); //$NON-NLS-1$ //$NON-NLS-2$
					b.setNodeset(child.getAttributeValue("", "nodeset")); //$NON-NLS-1$ //$NON-NLS-2$
					b.setRelevancy(child.getAttributeValue("", "relevant")); //$NON-NLS-1$ //$NON-NLS-2$
					if (child.getAttributeValue("", "required") != null)
						b.setRequired(child.getAttributeValue("", "required")); //$NON-NLS-1$ //$NON-NLS-2$
					String type = child.getAttributeValue("", "type"); //$NON-NLS-1$ //$NON-NLS-2$
					if (type.indexOf(':') > 0)
						type = type.substring(type.indexOf(':') + 1);
					b.setType(type);
					form.addBinding(b);
				} else if (TextUtil.equalsIgnoreCase(tagname,"input")) { //$NON-NLS-1$
					Prompt prompt = new Prompt();
					prompt.setFormControlType(Constants.INPUT);
					String ref = child.getAttributeValue(null, "ref"); //$NON-NLS-1$
					String bind = child.getAttributeValue(null, "bind"); //$NON-NLS-1$
					attachBind(form, prompt, ref, bind);
					String relevant = child.getAttributeValue(null, "relevant"); //$NON-NLS-1$
					if (relevant != null){
						prompt.setRelevantString(relevant);
					}
					prompt = parseElement(form, child, prompt);
					form.addPrompt(prompt);

				} else if (TextUtil.equalsIgnoreCase(tagname,"select1")) { //$NON-NLS-1$
					Prompt prompt = new Prompt();
					String ref = child.getAttributeValue(null, "ref"); //$NON-NLS-1$
					String bind = child.getAttributeValue(null, "bind"); //$NON-NLS-1$
					attachBind(form, prompt, ref, bind);
					String appearance = child.getAttributeValue(null, "appearance");
					//if (appearance == null) {
					//	appearance = "minimal";
					//}
					prompt.setAppearanceString(appearance);
					prompt.setFormControlType(Constants.SELECT1);
					prompt.setReturnType(org.javarosa.clforms.api.Constants.RETURN_SELECT1);
					prompt.setSelectMap(new SimpleOrderedHashtable());
					String relevant = child.getAttributeValue(null, "relevant"); //$NON-NLS-1$
					if (relevant != null){
						prompt.setRelevantString(relevant);
					}
					prompt = parseElement(form, child, prompt);
					prompt.localizeSelectMap(null);  //even fixed <label>s are stored in selectIDMap, and must be copied over
					form.addPrompt(prompt);
				} else if (TextUtil.equalsIgnoreCase(tagname,"select")) { //$NON-NLS-1$
					Prompt prompt = new Prompt();
					String ref = child.getAttributeValue(null, "ref"); //$NON-NLS-1$
					String bind = child.getAttributeValue(null, "bind"); //$NON-NLS-1$
					attachBind(form, prompt, ref, bind);
					String appearance = child.getAttributeValue(null, "appearance");
					//if (appearance == null) {
					//	appearance = "minimal";
					//}
					prompt.setAppearanceString(appearance);
					prompt.setFormControlType(Constants.SELECT);
					prompt.setReturnType(org.javarosa.clforms.api.Constants.RETURN_SELECT_MULTI);
					prompt.setSelectMap(new SimpleOrderedHashtable());
					String relevant = child.getAttributeValue(null, "relevant"); //$NON-NLS-1$
					if (relevant != null){
						prompt.setRelevantString(relevant);
					}
					prompt = parseElement(form, child, prompt);
					prompt.localizeSelectMap(null);  //even fixed <label>s are stored in selectIDMap, and must be copied over
					form.addPrompt(prompt);
				} else if (TextUtil.equalsIgnoreCase(tagname, "output")) { //$NON-NLS-1$
					Prompt prompt = new Prompt();
					String ref = child.getAttributeValue(null, "ref"); //$NON-NLS-1$
					String bind = child.getAttributeValue(null, "bind"); //$NON-NLS-1$
					attachBind(form, prompt, ref, bind);
					String type = child.getAttributeValue(null, "type");
					if (type == null) {
						type = "";
					}
					prompt.setTypeString(type);
					if (TextUtil.equalsIgnoreCase(type, "graph")) {
						prompt.setFormControlType(Constants.OUTPUT_GRAPH);
					}
					prompt.setReturnType(org.javarosa.clforms.api.Constants.RETURN_STRING);
					// prompt.setGraphDataMap(new SimpleOrderedHashtable());
					prompt = parseElement(form, child, prompt);
					form.addPrompt(prompt);
				} else if (TextUtil.equalsIgnoreCase(tagname, "trigger")) {
					Prompt prompt = new Prompt();
					String ref = child.getAttributeValue(null, "ref"); //$NON-NLS-1$
					String bind = child.getAttributeValue(null, "bind"); //$NON-NLS-1$
					attachBind(form, prompt, ref, bind);
					prompt.setFormControlType(Constants.TRIGGER);
					prompt.setReturnType(org.javarosa.clforms.api.Constants.RETURN_TRIGGER);
					prompt = parseElement(form, child, prompt);
					form.addPrompt(prompt);
				} else if (TextUtil.equalsIgnoreCase(tagname,"label")) { //$NON-NLS-1$
					label = getXMLText(child, 0, true);
					String ref = child.getAttributeValue("", "ref"); //$NON-NLS-1$
					if (ref != null) {
						if (ref.startsWith("jr:itext('") && ref.endsWith("')")) {
							textRef = ref.substring("jr:itext('".length(), ref.indexOf("')"));
						} else {
							throw new RuntimeException("malformed ref for <label>");
						}
					}
				} else if (TextUtil.equalsIgnoreCase(tagname,"hint")) { //$NON-NLS-1$
					String hint = getXMLText(child, 0, true);
					String ref = child.getAttributeValue("", "ref"); //$NON-NLS-1$
					if (ref != null) {
						if (ref.startsWith("jr:itext('") && ref.endsWith("')")) {
							ref = ref.substring("jr:itext('".length(), ref.indexOf("')"));
						} else {
							throw new RuntimeException("malformed ref for <hint>");
						}
					}
					
					if (ref == null) {
						existingPrompt.setHint(hint);
					} else {
						if (!hasITextMapping(form, ref))
							throw new RuntimeException("<hint> text is not localizable for all locales");
						existingPrompt.setHintTextId(ref, null);
					}
				} else if (TextUtil.equalsIgnoreCase(tagname,"item")) { //$NON-NLS-1$
					parseElement(form, child, existingPrompt);
					// TODO possible need to handle this return
				} else if (TextUtil.equalsIgnoreCase(tagname,"value")) { //$NON-NLS-1$
					value = getXMLText(child, 0, true);
				} else if (TextUtil.equalsIgnoreCase(tagname,"textbox")) {
					System.out.println("found textbox");
					Prompt prompt = new Prompt();
					prompt.setFormControlType(Constants.TEXTBOX);
					String ref = child.getAttributeValue(null, "ref"); //$NON-NLS-1$
					String bind = child.getAttributeValue(null, "bind"); //$NON-NLS-1$
					if (bind != null) {
						Binding b = (Binding) form.getBindings().get(bind);
						if (b != null) {
							prompt.setBindID(bind);
							prompt.setXpathBinding(b.getNodeset());
							prompt.setReturnType(getReturnTypeFromString(b.getType()));
							prompt.setId(b.getId());
						}
					} else if (ref != null) {
						prompt.setXpathBinding(ref);
						prompt.setId(getLastToken(ref, '/'));
						
					}
					String relevant = child.getAttributeValue(null, "relevant"); //$NON-NLS-1$
					if (relevant != null){
						prompt.setRelevantString(relevant);
					}
					prompt = parseElement(form, child, prompt);
					form.addPrompt(prompt);
				} else{ // tagname not recognised
					parseElement(form, child, null);
					// TODO possible need to handle this return
				}
				// TODO - how are other elements like html:p or br handled?
			}
		}		
		
		if (!value.equals("")) {
			if (!textRef.equals("")) {
				if (!hasITextMapping(form, textRef))
					throw new RuntimeException("<label> text is not localizable for all locales");
				existingPrompt.addSelectItemID(textRef, true, value);
			} else if (!label.equals("")) {
				existingPrompt.addSelectItemID(label, false, value);
			}
		} else {
			if (!textRef.equals("")) {
				if (!(hasITextMapping(form, textRef) ||
					  (hasITextMapping(form, textRef + ";long") && hasITextMapping(form, textRef + ";short"))))
					throw new RuntimeException("<label> text is not localizable for all locales");
				existingPrompt.setLongTextId(textRef + ";long", null);
				existingPrompt.setShortTextId(textRef + ";short", null);
			} else if (!label.equals("")) {
				existingPrompt.setLongText(label);
				existingPrompt.setShortText(label);
			}			
		}
				
		return existingPrompt;
	}

	/**
	 * KNOWN ISSUES WITH ITEXT
	 * 
	 * 'long' and 'short' forms of text are only supported for input control labels at this time. all other
	 * situations (<hint> tags, <label>s within <item>s, etc.) should only reference text handles that have
	 * only the single, default form.
	 */
	
	private static void parseIText (Form f, Element itext) {
		Localizer l = new Localizer(true, true);
		f.setLocalizer(l);
		l.registerLocalizable(f);
		
		for (int i = 0; i < itext.getChildCount(); i++) {
			Element trans = itext.getElement(i);
			if (trans == null || !trans.getName().equals("translation"))
				continue;
			
			parseTranslation(l, trans);
		}
		
		if (l.getAvailableLocales().length == 0)
			throw new RuntimeException("no <translation>s defined");
		
		if (l.getDefaultLocale() == null)
			l.setDefaultLocale(l.getAvailableLocales()[0]);
	}
		
	private static void parseTranslation (Localizer l, Element trans) {
		String lang = trans.getAttributeValue("", "lang");
		if (lang == null || lang.length() == 0)
			throw new RuntimeException("no language specified for <translation>");
		String isDefault = trans.getAttributeValue("", "default");
		
		if (!l.addAvailableLocale(lang))
			throw new RuntimeException("duplicate <translation> for same language");
		
		if (isDefault != null) {
			if (l.getDefaultLocale() != null)
				throw new RuntimeException("more than one <translation> set as default");
			l.setDefaultLocale(lang);
		}
		
		for (int j = 0; j < trans.getChildCount(); j++) {
			Element text = trans.getElement(j);
			if (text == null || !text.getName().equals("text"))
				continue;
			
			parseTextHandle(l, lang, text);
		}
		
	}
		
	private static void parseTextHandle (Localizer l, String locale, Element text) {
		String id = text.getAttributeValue("", "id");
		if (id == null || id.length() == 0)
			throw new RuntimeException("no id defined for <text>");

		for (int k = 0; k < text.getChildCount(); k++) {
			Element value = text.getElement(k);
			if (value == null || !value.getName().equals("value"))
				continue;

			String form = value.getAttributeValue("", "form");
			if (form != null && form.length() == 0)
				form = null;
			String data = getXMLText(value, 0, true);
			if (data == null)
				data = "";

			String textID = (form == null ? id : id + ";" + form);  //kind of a hack
			if (l.hasMapping(locale, textID))
				throw new RuntimeException("duplicate definition for text ID and form");
			l.setLocaleMapping(locale, textID, data);
		}
	}

	private static boolean hasITextMapping (Form form, String textID) {
		Localizer l = form.getLocalizer();
		return l.hasMapping(l.getDefaultLocale(), textID);
	}
		
	private static void attachBind(Form form, Prompt prompt, String ref,
			String bind) {
		if (bind != null) {
			Binding b = (Binding) form.getBindings().get(bind);
			if (b != null) {
				prompt.setBindID(bind);
				prompt.setXpathBinding(b.getNodeset());
				prompt.setReturnType(getReturnTypeFromString(b.getType()));
				prompt.setId(b.getId());
				prompt.setBind(b);
				// LOG
				//System.out.println(prompt.getLongText()+" attached to Bind = "+prompt.getBind().toString());
			}
			else
				//LOG
				System.out.println("MATCHING BIND not found");
		} else if (ref != null) {
			prompt.setXpathBinding(ref);
			prompt.setId(getLastToken(ref, '/'));
		}
	}
	
	/**
	 * Method to parse an input stream into an existing XForm.
	 * 
	 * @param inputStreamReader, XForm
	 * @return XForm
	 */
	public static void parseModel(InputStreamReader isr, Model model) {

		try {
			KXmlParser parser = new KXmlParser();
			parser.setInput(isr);
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
			Document doc = new Document();
			doc.parse(parser);

			Element root = doc.getRootElement();
			
			model.setXmlModel(doc);
			//TODO FIGURE out why have to trim twice!
			model.trimXML();
	        model.trimXML();
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	

	private static String getLastToken(String ref, char c) {
		StringTokenizer tok = new StringTokenizer(ref, c);
		String last = ""; //$NON-NLS-1$
		while (tok.hasMoreTokens())
			last = tok.nextToken();
		return last;
	}

	/**
	 * @param type
	 * @return
	 */
	private static int getReturnTypeFromString(String type) {
		int index = type.indexOf(':');
		if (index > 0)
			type = type.substring(index + 1);

		if (TextUtil.equalsIgnoreCase(type,"int")) //$NON-NLS-1$
			return Constants.RETURN_INTEGER;
		else if (TextUtil.equalsIgnoreCase(type,"numeric")) //$NON-NLS-1$
			return Constants.RETURN_INTEGER;
		else if (TextUtil.equalsIgnoreCase(type,"date")) //$NON-NLS-1$
			return Constants.RETURN_DATE;
		else if (TextUtil.equalsIgnoreCase(type,"boolean")) //$NON-NLS-1$
			return Constants.RETURN_BOOLEAN;
		else
			return Constants.RETURN_STRING;
	}

	/**
	 * Opens a file and returns an InputStreamReader to it
	 * 
	 * @param file
	 * @return
	 */
	public static InputStreamReader getReader(String file) {

		InputStreamReader isr = null;
		try {
		  //#if app.usefileconnections
			FileConnection fc = (FileConnection) Connector.open(file);

			if (!fc.exists()) {
				throw new IOException(Locale.get("error.file"));
			} else {
				InputStream fis = fc.openInputStream();

				isr = new InputStreamReader(fis);
				/*
				 * fis.close(); fc.close();
				 */

			}
		  //#endif
		} catch (Exception e) {
			e.printStackTrace();
		}

		return isr;
	}

	/**
	 * Writes string parameter to a file
	 * 
	 * @param file
	 * @param writableString
	 */
	public static void writeStringToFile(String file, String writableString) {

		try {
			// "file://c:/myfile.txt;append=true"
			OutputConnection connection = (OutputConnection) Connector.open(
					file, Connector.WRITE);
			// TODO do something appropriate if the file does not exist
			OutputStream os = connection.openOutputStream();
			os.write(writableString.getBytes());
			os.flush();
			os.close();
		} catch (IOException e) {
			// TODO handle exception appropriately
			System.out.println(Locale.get("error.file.write")); //$NON-NLS-1$
			e.printStackTrace();
		}
	}

	/**
	 * Writes XML Document parameter to a file
	 * 
	 * @param file
	 * @param xml
	 */
	public static void writeXMLToFile(String file, Document xml)
			throws IOException {
		KXmlSerializer serializer = new KXmlSerializer();

		OutputConnection connection = (OutputConnection) Connector.open(file,
				Connector.WRITE);
		// TODO do something appropriate if the file does not exist
		OutputStream os = connection.openOutputStream();

		serializer.setOutput(os, null);
		xml.write(serializer);
		serializer.flush();

		os.close();
		connection.close();
	}

	public static String readTextFile(String url) throws IOException {
	    StringBuffer content = new StringBuffer();
	  //#if app.usefileconnections
		FileConnection fc = (FileConnection) Connector.open(url);

		if (!fc.exists()) {
			throw new IOException("File does not exists");
		}

		InputStream fis = fc.openInputStream();
		int ch;
		while ((ch = fis.read()) != -1) {
			
			content.append((char) ch);
		}


		fis.close();
		fc.close();

		//#else
		try {
		    throw new IOException("No file connection API available");
		}
		catch (IOException e) {
		    throw e;
		}
		finally
		//#endif
		{
	    return content.toString();
		}
	}

	public static void printModel(Document doc) throws IOException {
		KXmlSerializer serializer = new KXmlSerializer();
		serializer.setOutput(System.out, null);
		doc.write(serializer);
		serializer.flush();
	}
	
	
	/**
	 * Evaluates an Xpath expression on the xmlModel and returns a Vector result
	 * set.
	 * 
	 * @param string
	 * @return Vector result set
	 */
	public Vector evaluateXpath(Model xmlModel, String xpath) {
		XPathExpression xpls = new XPathExpression(xmlModel.getXmlModel(), xpath);
		return xpls.getResult();
	}
	
	public static String sendHttpGet(String url) {
		HttpConnection hcon = null;
		DataInputStream dis = null;
		StringBuffer responseMessage = new StringBuffer();

		try {
			// a standard HttpConnection with READ access
			hcon = (HttpConnection) Connector.open(url);

			// obtain a DataInputStream from the HttpConnection
			dis = new DataInputStream(hcon.openInputStream());

			// retrieve the response from the server
			int ch;
			while ((ch = dis.read()) != -1) {
				responseMessage.append((char) ch);
			}
		} catch (Exception e) {
			e.printStackTrace();
			responseMessage.append("ERROR");
		} finally {
			try {
				if (hcon != null)
					hcon.close();
				if (dis != null)
					dis.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		return responseMessage.toString();
	}

	public static String sendHttpPost(String url) {
		HttpConnection hcon = null;
		DataInputStream dis = null;
		DataOutputStream dos = null;
		StringBuffer responseMessage = new StringBuffer();
		// the request body
		String requeststring = "uname=simon" +
				"&pw=123abc" +
				"&redirect=%2FOPENMRSDTHF" +
				"&refererURL=http%3A%2F%2Ftambo.cell-life.org%3A8180%2FOPENMRSDTHF%2Flogin.htm";

		try {
			// an HttpConnection with both read and write access
			hcon = (HttpConnection) Connector.open(url, Connector.READ_WRITE);

			// set the request method to POST
			hcon.setRequestMethod(HttpConnection.POST);
			
			// obtain DataOutputStream for sending the request string
			dos = hcon.openDataOutputStream();
			byte[] request_body = requeststring.getBytes();

			// send request string to server
			for (int i = 0; i < request_body.length; i++) {
				dos.writeByte(request_body[i]);
			}// end for( int i = 0; i < request_body.length; i++ )

			// obtain DataInputStream for receiving server response
			dis = new DataInputStream(hcon.openInputStream());

			// retrieve the response from server
			int ch;
			while ((ch = dis.read()) != -1) {
				responseMessage.append((char) ch);
			}// end while( ( ch = dis.read() ) != -1 ) {
		} catch (Exception e) {
			e.printStackTrace();
			responseMessage.append("ERROR");
		} finally {
			// free up i/o streams and http connection
			try {
				if (hcon != null)
					hcon.close();
				if (dis != null)
					dis.close();
				if (dos != null)
					dos.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}// end try/catch
		}// end try/catch/finally
		return responseMessage.toString();
	}// end sendHttpPost( String )
	
	//reads all subsequent text nodes and returns the combined string
	public static String getXMLText (Node node, int i, boolean trim) {
		StringBuffer strBuff = null;
		
		String text = node.getText(i);
		if (text == null)
			return null;
		
		for (i++; i < node.getChildCount() && node.getType(i) == Node.TEXT; i++) {
			if (strBuff == null)
				strBuff = new StringBuffer(text);

			strBuff.append(node.getText(i));
		}			
		if (strBuff != null)
			text = strBuff.toString();
		
		if (trim)
			text = text.trim();
		
		return text;
	}
}
