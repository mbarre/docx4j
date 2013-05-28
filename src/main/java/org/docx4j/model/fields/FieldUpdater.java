package org.docx4j.model.fields;

import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.apache.log4j.Logger;
import org.docx4j.TraversalUtil;
import org.docx4j.jaxb.Context;
import org.docx4j.model.fields.docproperty.DocPropertyResolver;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.JaxbXmlPart;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.CTSimpleField;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;

/**
 * Refreshes the values of certain fields in the
 * docx (currently DOCPROPERTY only).
 * 
 * Do this whether they are simple or complex.
 * 
 * This updates the docx.  If you don't want to do
 * that, apply it to a clone instead.
 * 
 * @author jharrop
 *
 */
public class FieldUpdater {
	
	private static Logger log = Logger.getLogger(FieldUpdater.class);			
	
	WordprocessingMLPackage wordMLPackage;
	DocPropertyResolver docPropertyResolver;
	
	StringBuilder report = null;
	
	public FieldUpdater(WordprocessingMLPackage wordMLPackage) {
		this.wordMLPackage = wordMLPackage;
//		docPropsCustomPart = wordMLPackage.getDocPropsCustomPart();
		docPropertyResolver = new DocPropertyResolver(wordMLPackage);
	}

	public void update(boolean processHeadersAndFooters) throws Docx4JException {

		report = new StringBuilder();
		
		updatePart(wordMLPackage.getMainDocumentPart() );

		if (processHeadersAndFooters) {
			
			RelationshipsPart rp = wordMLPackage.getMainDocumentPart().getRelationshipsPart();
			for ( Relationship r : rp.getJaxbElement().getRelationship()  ) {
				
				if (r.getType().equals(Namespaces.HEADER)
						|| r.getType().equals(Namespaces.FOOTER)) {
					
					JaxbXmlPart part = (JaxbXmlPart)rp.getPart(r);
					
					report.append("\n" + part.getPartName() + "\n");
										
					log.debug("\n" + part.getPartName() + "\n");
					updatePart(part );
//						performOnInstance(
//								((ContentAccessor)part).getContent() );
					
				}			
			}	
		}	
		
		log.info(report.toString());
	}
	
	public void updatePart(JaxbXmlPart part) throws Docx4JException {

		updateSimple(part);
		updateComplex(part);
	}
	
	public void updateSimple(JaxbXmlPart part) throws Docx4JException {
		
		FldSimpleModel fsm = new FldSimpleModel(); //gets reused
		List contentList = ((ContentAccessor)part).getContent();
		
		// find fields
		SimpleFieldLocator fl = new SimpleFieldLocator();
		new TraversalUtil(contentList, fl);
		
		report.append("\n\nSimple Fields in " + part.getPartName() + "\n");
		report.append("============= \n");
		report.append("Found " + fl.simpleFields.size() + " simple fields \n ");
		
		for( CTSimpleField simpleField : fl.simpleFields ) {
			
			//System.out.println(XmlUtils.marshaltoString(simpleField, true, true));
//			System.out.println(simpleField.getInstr());
			
			if ("DOCPROPERTY".equals(FldSimpleUnitsHelper.getFldSimpleName(simpleField.getInstr()))) {
				//only parse those fields that get processed
				try {
					fsm.build(simpleField.getInstr());
				} catch (TransformerException e) {
					e.printStackTrace();
				}
				
				String key = fsm.getFldParameterString();
				
				String val;
				try {
				  val = (String) docPropertyResolver.getValue(key); 
				} catch (FieldValueException e) {
					report.append( simpleField.getInstr() + "\n");
					report.append( "!-> NOT FOUND! \n");	
					continue;
				}
				
				if (val==null) {
					
					report.append( simpleField.getInstr() + "\n");
					report.append( "!-> NOT FOUND! \n");
					
				} else {
							//docPropsCustomPart.getProperty(key);
	//				System.out.println(val);
					val = FldSimpleUnitsHelper.applyFormattingSwitch(fsm, val);
	//				System.out.println("--> " + val);
					report.append( simpleField.getInstr() + "\n");
					report.append( "--> " + val + "\n");
					
					R r=null;
					if (simpleField.getInstr().toUpperCase().contains("MERGEFORMAT")) {					
						// find the first run and use the formatting of that
						r = getFirstRun(simpleField.getContent());					
					} 
					if (r==null) {
						r = Context.getWmlObjectFactory().createR();
					} else {
						r.getContent().clear();
					}
					simpleField.getContent().clear();	
					simpleField.getContent().add(r);
					Text t = Context.getWmlObjectFactory().createText();
					t.setValue(val);
					// t.setSpace(value) //TODO
					r.getContent().add(t);
					
	//				System.out.println(XmlUtils.marshaltoString(simpleField, true, true));
				}
				
			} else {
				
				report.append("Ignoring " + simpleField.getInstr() + "\n");
				
			}
		}
		
	}
	
	private R getFirstRun(List<Object> content) {
		
		for (Object o : content) {
			if (o instanceof R) return (R)o;
		}
		return null;
	}

	public void updateComplex(JaxbXmlPart part) throws Docx4JException {
		
		FldSimpleModel fsm = new FldSimpleModel(); //gets reused
		List contentList = ((ContentAccessor)part).getContent();
		
		ComplexFieldLocator fl = new ComplexFieldLocator();
		new TraversalUtil(contentList, fl);
		
		report.append("\n Complex Fields in "+ part.getPartName() + "\n");
		report.append("============== \n");
		
		report.append("Found " + fl.getStarts().size() + " fields \n");
		
		
		// canonicalise and setup fieldRefs 
		List<FieldRef> fieldRefs = new ArrayList<FieldRef>();
		for( P p : fl.getStarts() ) {
			int index;
			if (p.getParent() instanceof ContentAccessor) {
				index = ((ContentAccessor)p.getParent()).getContent().indexOf(p);
				P newP = FieldsPreprocessor.canonicalise(p, fieldRefs);
//				log.debug("NewP length: " + newP.getContent().size() );
				((ContentAccessor)p.getParent()).getContent().set(index, newP);
			} else if (p.getParent() instanceof java.util.List) {
				// This does happen!
				index = ((java.util.List)p.getParent()).indexOf(p);
				P newP = FieldsPreprocessor.canonicalise(p, fieldRefs);
//				log.debug("NewP length: " + newP.getContent().size() );
				((java.util.List)p.getParent()).set(index, newP);				
			} else {
				throw new Docx4JException ("Unexpected parent: " + p.getParent().getClass().getName() );
			}
		}
		
		// Populate
		for (FieldRef fr : fieldRefs) {
			
			if ("DOCPROPERTY".equals(FldSimpleUnitsHelper.getFldSimpleName(fr.getInstr()))) {
				try {
					fsm.build(fr.getInstr());
				} catch (TransformerException e) {
					e.printStackTrace();
				}

				String key = fsm.getFldParameterString();
				String val = (String) docPropertyResolver.getValue(key); 
				try {
				  val = (String) docPropertyResolver.getValue(key); 
				} catch (FieldValueException e) {
					report.append( fr.getInstr() + "\n");
					report.append( "!-> NOT FOUND! \n");	
					continue;
				}
				
				if (val==null) {
					
					report.append( fr.getInstr() + "\n");
					report.append( "!-> NOT FOUND! \n");
					
				} else {
				
	//				System.out.println(val);
					val = FldSimpleUnitsHelper.applyFormattingSwitch(fsm, val);
	//				System.out.println("--> " + val);
					report.append( fr.getInstr() + "\n");
					report.append( "--> " + val + "\n");
	
					fr.setResult(val);
					
	//				// If doing an actual mail merge, the begin-separate run is removed, as is the end run
	//				fr.getParent().getContent().remove(fr.getBeginRun());
	//				fr.getParent().getContent().remove(fr.getEndRun());
					
//					System.out.println(XmlUtils.marshaltoString(
//							fr.getParent(), true, true));
				}				
			} else {
				report.append("Ignoring " + fr.getInstr() + "\n");				
			}
		}	}
	
	/**
	 * @param args
	 * @throws Docx4JException 
	 */
	public static void main(String[] args) throws Docx4JException {

		String filenames[] = {"CL FORMALIZACION Vigilancia.docx","CL FORMALIZACION Vigilancia2.docx","Contrato de Ejecucion de Obra AQN130121 Nº1 EJEMPLO_TABULACION.DOCX","Contrato Puesta a Disposición (ETTs) AQN120612 Nº1.docx","RFQ _test.docx","RFQ_EETT_CS.docx","RFQ_General_CS.docx"};		
//    	inputfilepath = System.getProperty("user.dir") + "/Aq_Obra.docx";
		
		int docIndex = 6;
		WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(
				new java.io.File(
						System.getProperty("user.dir") + "/aq/" 
								+ filenames[docIndex]));
		
		FieldUpdater fu = new FieldUpdater(wordMLPackage);
		fu.update(true);
		
//		System.out.println(XmlUtils.marshaltoString(wordMLPackage.getMainDocumentPart().getJaxbElement(), true, true));
		
		System.out.println(filenames[docIndex]);
	}

}
