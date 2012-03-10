
package mesquite.gataga.IntrepretFASTAtoNucFreq;
/*~~  */

import java.util.*;
import java.awt.*;
import java.awt.event.*;

import mesquite.gataga.lib.*;
import mesquite.genesis.lib.StateFreqModel;
import mesquite.lib.*;
import mesquite.lib.characters.*;
import mesquite.lib.duties.*;
import mesquite.cont.lib.*;
import mesquite.categ.lib.*;
import mesquite.molec.lib.*;

public class IntrepretFASTAtoNucFreq extends FileInterpreterI  implements ItemListener{
	long A = CategoricalState.makeSet(0);
	long C = CategoricalState.makeSet(1);
	long G = CategoricalState.makeSet(2);
	long T = CategoricalState.makeSet(3);
	long AT = CategoricalState.setUncertainty(A | T, true);
	long CG =  CategoricalState.setUncertainty(C | G, true);
	long ACGT = CategoricalState.setUncertainty(A | C | G | T,true);

	static int charLength = 0;
	static int charA = 1;
	static int charC = 2;
	static int charG = 3;
	static int charT = 4;
	static int charAT = 5;
	static int charCG = 6;
	static int numChars = 7;

	Blaster blasterTask;
	int numBlastSeparateCriteria = 3;
	BlastSeparateCriterion[] blastSequesterCriteriaTask = new BlastSeparateCriterion[numBlastSeparateCriteria];

	Class[] acceptedClasses;

	static final int DONTBLAST = 0;
	static final int BLAST = 1;
	static final int BLASTX = 2;
	int blastOption = DONTBLAST;
	int lowerBlastSequenceLength = 1000;
	boolean saveTopHits = false;
	boolean resaveFastaFile = false;
	double eValueCutoff = 10.0;
	int numHits = 5;
	//	boolean idIsNameInLocalFastaFile = false;
	String localFastaFilePath = null;

	boolean fetchTaxonomy = false;

	public void getEmployeeNeeds(){  //This gets called on startup to harvest information; override this and inside, call registerEmployeeNeed
		EmployeeNeed e = registerEmployeeNeed(Blaster.class, getName() + "  needs a Blast module.","");
		EmployeeNeed e2 = registerEmployeeNeed(BlastSeparateCriterion.class, getName() + "  needs a module to separate blast hits.","");
	}

	/*.................................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		acceptedClasses = new Class[] {ContinuousState.class};

		for (int i = 0; i < numBlastSeparateCriteria; i++) {
			blastSequesterCriteriaTask[i] = (BlastSeparateCriterion)hireNamedEmployee(BlastSeparateCriterion.class, "#aBlastNoCriterion");
		}

		loadPreferences();
		return true;  
	}
	/*.................................................................................................................*/
	public void processSingleXMLPreference (String tag, String content) {
		if ("fetchTaxonomy".equalsIgnoreCase(tag))
			fetchTaxonomy = MesquiteBoolean.fromTrueFalseString(content);
		else if ("saveTopHits".equalsIgnoreCase(tag))
			saveTopHits = MesquiteBoolean.fromTrueFalseString(content);
		else if ("resaveFastaFile".equalsIgnoreCase(tag))
			resaveFastaFile = MesquiteBoolean.fromTrueFalseString(content);
		else if ("blastOption".equalsIgnoreCase(tag))
			blastOption = MesquiteInteger.fromString(content);
		else if ("lowerBlastSequenceLength".equalsIgnoreCase(tag))
			lowerBlastSequenceLength = MesquiteInteger.fromString(content);
		else if ("numHits".equalsIgnoreCase(tag))
			numHits = MesquiteInteger.fromString(content);		
		else if ("eValueCutoff".equalsIgnoreCase(tag))
			eValueCutoff = MesquiteDouble.fromString(content);		
	}
	/*.................................................................................................................*/
	public String preparePreferencesForXML () {
		StringBuffer buffer = new StringBuffer(60);	
		StringUtil.appendXMLTag(buffer, 2, "fetchTaxonomy", fetchTaxonomy);  
		StringUtil.appendXMLTag(buffer, 2, "saveTopHits", saveTopHits);  
		StringUtil.appendXMLTag(buffer, 2, "resaveFastaFile", resaveFastaFile);  
		StringUtil.appendXMLTag(buffer, 2, "blastOption", blastOption);  
		StringUtil.appendXMLTag(buffer, 2, "lowerBlastSequenceLength", lowerBlastSequenceLength);  
		StringUtil.appendXMLTag(buffer, 2, "numHits", numHits);  
		StringUtil.appendXMLTag(buffer, 2, "eValueCutoff", eValueCutoff);  
		return buffer.toString();
	}
	/*.................................................................................................................*/
	
	Choice[] blastSeparateCriterionChoice;
	Button[] criterionOption;
	/*.................................................................................................................*/
	public boolean queryOptions() {
		MesquiteInteger buttonPressed = new MesquiteInteger(1);
		ExtensibleDialog dialog = new ExtensibleDialog(containerOfModule(), getName() + " Options",buttonPressed);  //MesquiteTrunk.mesquiteTrunk.containerOfModule()
		dialog.addLabel(getName() + " Options");

		RadioButtons blastRadioButtons= dialog.addRadioButtons(new String[] {"don't BLAST", "BLAST", "BLASTX"}, blastOption);
		IntegerField blastLowerLengthField = dialog.addIntegerField("Lower length limit of sequences to be BLASTed:", lowerBlastSequenceLength, 10, 10, Integer.MAX_VALUE);
		IntegerField numHitsField = dialog.addIntegerField("Number of top hits:", numHits, 8, 1, Integer.MAX_VALUE);
		DoubleField eValueCutoffField = dialog.addDoubleField("Reject hits with eValues greater than: ", eValueCutoff, 20, 0.0, Double.MAX_VALUE);
		Checkbox resaveFastaFileBox = dialog.addCheckBox("Save copy of original FASTA file but with names appended with top hit", resaveFastaFile);
		dialog.addHorizontalLine(2);
		Checkbox saveTopHitsBox = dialog.addCheckBox("Save top hits to new FASTA files", saveTopHits);


		blastSeparateCriterionChoice = new Choice[numBlastSeparateCriteria];
		criterionOption = new Button[numBlastSeparateCriteria];

		for (int i=0; i<numBlastSeparateCriteria; i++){
			blastSeparateCriterionChoice[i] = dialog.addPopUpMenu(""+(i+1)+". Separate FASTA files based upon: ", BlastSeparateCriterion.class, dialog.getModuleClassNumber(BlastSeparateCriterion.class, blastSequesterCriteriaTask[i].getClassName()));
			dialog.suppressNewPanel();
			criterionOption[i] = dialog.addAListenedButton("Options...", null, blastSequesterCriteriaTask[i]);
			dialog.forceNewPanel();
			blastSeparateCriterionChoice[i].addItemListener(this);
		}

		//		Button editCriterion1Button = dialog.addAListenedButton("Options...", null, this);
		//		editCriterion1Button.setActionCommand("QueryOptions");
		//		newEquilStatesModelChoice.setActionListener("QueryOptions");
		//		editCriterion1Button.setActionListener(editCriterionButton[i]);


		dialog.addHorizontalLine(2);
		Checkbox fetchTaxonomyBox = dialog.addCheckBox("Fetch taxonomic information", fetchTaxonomy);

		dialog.completeAndShowDialog(true);
		if (buttonPressed.getValue()==0)  {
			blastOption = blastRadioButtons.getValue();
			lowerBlastSequenceLength = blastLowerLengthField.getValue();
			eValueCutoff = eValueCutoffField.getValue();
			numHits = numHitsField.getValue();
			saveTopHits = saveTopHitsBox.getState();
			resaveFastaFile = resaveFastaFileBox.getState();
			fetchTaxonomy = fetchTaxonomyBox.getState();
			storePreferences();
		}
		dialog.dispose();
		return (buttonPressed.getValue()==0);
	}
	public String[] getModuleList(Class dutyClass) {
		StringArray stringArray = new StringArray(5);
		if (dutyClass!=null) {  //module dutyClass specified; need to create list of modules to choose
			MesquiteModuleInfo mbi=null;
			int count = 0;
			while (count++<128 && (mbi = MesquiteTrunk.mesquiteModulesInfoVector.findNextModule(dutyClass, mbi))!=null) {
				if (mbi.getUserChooseable()) {
					stringArray.addAndFillNextUnassigned(mbi.getName());
				}
			}
			return stringArray.getFilledStrings();
		}
		return null;
	}
	/*.................................................................................................................*/
	public String getModuleClassName(Class dutyClass, int index) {
		if (dutyClass!=null) { 
			MesquiteModuleInfo mbi=null;
			int count = -1;
			while (count++<128 && (mbi = MesquiteTrunk.mesquiteModulesInfoVector.findNextModule(dutyClass, mbi))!=null) {
				if (mbi.getUserChooseable()) {
					if (count==index)
						return mbi.getClassName();
				}
			}
		}
		return null;
	}
	/*.................................................................................................................*/
	public void hireBlastCriterion(int whichCriterion, int index) {  
		String name = getModuleClassName(BlastSeparateCriterion.class, index);
		if (StringUtil.notEmpty(name)) {
			BlastSeparateCriterion newCriterion = (BlastSeparateCriterion)hireNamedEmployee(BlastSeparateCriterion.class, "#"+name);
			if (newCriterion!=null) {
				criterionOption[whichCriterion].removeActionListener(blastSequesterCriteriaTask[whichCriterion] );
				blastSequesterCriteriaTask[whichCriterion] = newCriterion;
				criterionOption[whichCriterion].addActionListener(blastSequesterCriteriaTask[whichCriterion] );
			}
		}
	}
	/*.................................................................................................................*/
  	public void itemStateChanged(ItemEvent e){
		for (int i=0; i<numBlastSeparateCriteria; i++){
	 		if (e.getItemSelectable() == blastSeparateCriterionChoice[i]){
	 			int index = blastSeparateCriterionChoice[i].getSelectedIndex();
	 			hireBlastCriterion(i, index);
	  		}
		}
  	}
	/*.................................................................................................................*/
	public boolean isPrerelease() {  
		return true;  
	}
	/*.................................................................................................................*/
	public boolean canExportEver() {  
		return false;  
	}
	/*.................................................................................................................*/
	public boolean canExportProject(MesquiteProject project) {  
		return false;
	}
	/*.................................................................................................................*/
	public boolean canExportData(Class dataClass) {  
		return false; 
	}


	/*.................................................................................................................*/
	public boolean canImport(){
		return true;
	}

	/** Returns whether the module can read (import) files considering the passed argument string (e.g., fuse) */
	public boolean canImport(String arguments){
		return true;
	}

	/*.................................................................................................................*/
	public CharacterData createData(CharactersManager charTask, Taxa taxa) {  
		return charTask.newCharacterData(taxa, numChars, ContinuousData.DATATYPENAME);  //
	}
	/*.................................................................................................................*/
	private void createCriteriaDirectories(String directory,int taskNumber){
		if (taskNumber>=blastSequesterCriteriaTask.length)
			return;
		if (blastSequesterCriteriaTask[taskNumber]!=null && blastSequesterCriteriaTask[taskNumber].isActive()) {
			String[] newDirectories = blastSequesterCriteriaTask[taskNumber].getSubdirectoryNames();
			if (newDirectories!=null) {
				for (int j=0; j<newDirectories.length; j++){
					String newDir = directory  + newDirectories[j];
					MesquiteFile.createDirectory(newDir);
					createCriteriaDirectories(newDir+ MesquiteFile.fileSeparator, taskNumber+1);
				}	
			}
		}

	}

	/*.................................................................................................................*/
	private void saveInCriterionDirectory(BLASTResults blastResults, String fasta, String directory, String sequenceName, int taskNumber){
		if (taskNumber>=blastSequesterCriteriaTask.length){
			MesquiteFile.putFileContents(directory + sequenceName, fasta, true);
			return;
		}
		if (blastSequesterCriteriaTask[taskNumber]!=null && blastSequesterCriteriaTask[taskNumber].isActive()) {
			int match = blastSequesterCriteriaTask[taskNumber].getCriterionMatch(blastResults);
			if (match>=0) {
				String newDir = blastSequesterCriteriaTask[taskNumber].getDirectoryName(match);
				if (StringUtil.notEmpty(newDir))
					newDir = directory + newDir +MesquiteFile.fileSeparator;
				else
					newDir = directory;
				saveInCriterionDirectory(blastResults, fasta, newDir, sequenceName, taskNumber+1);
			}	
		} else if (taskNumber==blastSequesterCriteriaTask.length-1){
			MesquiteFile.putFileContents(directory + sequenceName, fasta, true);
		}
	}

	/*.................................................................................................................*/
	public void readFileCore(Parser parser, MesquiteFile file, ContinuousData data, Taxa taxa, int lastTaxonNumber, ProgressIndicator progIndicator, String arguments) {
		if (blasterTask==null)
			return;

		boolean wassave = data.saveChangeHistory;
		data.saveChangeHistory = false;
		Parser subParser = new Parser();
		long pos = 0;
		StringArray accessionNumbers = new StringArray(numHits);

		StringBuffer sb = new StringBuffer(1000);
		if (file!=null)
			file.readLine(sb);
		else
			sb.append(parser.getRawNextLine());
		String line = sb.toString();
		int taxonNumber = -1;

		boolean abort = false;
		subParser.setString(line); //sets the string to be used by the parser to "line" and sets the pos to 0
		subParser.setPunctuationString(">");
		parser.setPunctuationString(">");
		String token = subParser.getFirstToken(line); //should be >
		int charAdded = 0;
		int numFilledChars = data.getNumChars();
		boolean added = false;
		boolean replaceExisting = false;
		StringBuffer response = new StringBuffer();
		StringBuffer blastReport = new StringBuffer();
		StringBuffer fastaBLASTResults = new StringBuffer();
		boolean storeBlastSequences = saveTopHits;
		String pathForBLASTfiles = null;
		String pathForBestFastaFiles = null;
		String pathForOtherFastaFiles = null;
		String subDirectoryForMatchedFiles = "Hits match criterion"+MesquiteFile.fileSeparator;
		String subDirectoryForUnmatchedFiles = "Hits don't match criterion"+MesquiteFile.fileSeparator;
		String pathForBlastReport = null;
		String pathForResavedFile = null;
		String pathForResavedBestHitsSequesterFile = null;
		String pathForResavedOtherHitsSequesterFile = null;

		if (blastOption==BLAST){
			loglnEchoToStringBuffer("\n============\nBLAST Search", blastReport);
			blasterTask.setBlastx(false);
		} else if (blastOption==BLASTX){
			loglnEchoToStringBuffer("\n============\nBLASTX Search", blastReport);
			blasterTask.setBlastx(true);
		}
		if (blastOption==BLAST|| blastOption==BLASTX){
			loglnEchoToStringBuffer("   Blasting sequences at least "+ lowerBlastSequenceLength + " bases long", blastReport);
			if (eValueCutoff>=0.0) 
				loglnEchoToStringBuffer("   Only keeping hits with e-values less than or equal to "+ eValueCutoff, blastReport);
			if (saveTopHits&& StringUtil.blank(pathForBLASTfiles)) {
				pathForBLASTfiles = MesquiteFile.chooseDirectory("Choose directory to store BLAST results: ");
				if (StringUtil.blank(pathForBLASTfiles))
					storeBlastSequences = false;
				else if (!pathForBLASTfiles.endsWith(MesquiteFile.fileSeparator))
					pathForBLASTfiles+=MesquiteFile.fileSeparator;
			}
			if (storeBlastSequences) {
				loglnEchoToStringBuffer("   Saving FASTA files for top blast hits", blastReport);

			//	int numBlastSeparateCriteria = 3;
			//	BlastSeparateCriterion[] blastSequesterCriteriaTask = new BlastSeparateCriterion[numBlastSeparateCriteria];

				createCriteriaDirectories(pathForBLASTfiles,0);
				for (int i=0;i<numBlastSeparateCriteria; i++) {
					if (blastSequesterCriteriaTask[i]!=null && blastSequesterCriteriaTask[i].isActive()) {
						loglnEchoToStringBuffer(blastSequesterCriteriaTask[i].getParameters(), blastReport);
					}
				}

			}
		}

		boolean someBlastsDone = false;

		if (blastOption==BLAST|| blastOption==BLASTX){
			loglnEchoToStringBuffer("\n============\n", blastReport);
			pathForBlastReport =pathForBLASTfiles+"BLAST Report";
			//			StringBuffer fileNameBuffer = new StringBuffer("BLAST Report");
			//			pathForBlastReport = MesquiteFile.saveFileAsDialog("Save BLAST report", fileNameBuffer);
			if (pathForBlastReport!=null)
				MesquiteFile.putFileContents(pathForBlastReport, blastReport.toString(), true);
			blastReport.setLength(0);
			if (resaveFastaFile){
				pathForResavedFile =pathForBLASTfiles+"Modified."+file.getName();
			}
			/*			if (storeBlastSequences && matchDefs){
				pathForResavedBestHitsSequesterFile =pathForBLASTfiles+"BestHitsCriteriaMatch."+file.getName();
				pathForResavedOtherHitsSequesterFile =pathForBLASTfiles+"OtherHitsCriteriaMatch."+file.getName();
			}
			 */
			//			fileNameBuffer.setLength(0);
			//			fileNameBuffer.append("MOD"+file.getName());
			//			if (resaveFastaFile){
			//				pathForResavedFile = MesquiteFile.saveFileAsDialog("Save Modified FASTA file", fileNameBuffer);
			//			}
		}


		data.setCharacterName(charLength, "length");
		data.setCharacterName(charA, "A");
		data.setCharacterName(charC, "C");
		data.setCharacterName(charG, "G");
		data.setCharacterName(charT, "T");
		data.setCharacterName(charAT, "AT");
		data.setCharacterName(charCG, "CG");

		while (!StringUtil.blank(line) && !abort) {

			//parser.setPunctuationString(null);

			token = subParser.getRemaining();  //taxon Name
			String sequenceName = token;
			taxonNumber = taxa.whichTaxonNumber(token);
			replaceExisting = false;


			if (data.getNumTaxa()<=lastTaxonNumber) {
				int numTaxaAdded = 1;
				if (lastTaxonNumber>10000)
					numTaxaAdded=500;
				else if (lastTaxonNumber>5000)
					numTaxaAdded=200;
				else if (lastTaxonNumber>2500) 
					numTaxaAdded=100;
				else if (lastTaxonNumber>1000)
					numTaxaAdded=50;
				else if (lastTaxonNumber>500)
					numTaxaAdded=10;
				taxa.addTaxa(lastTaxonNumber-1, numTaxaAdded, false);
				added=true;
				data.addTaxa(lastTaxonNumber-1, numTaxaAdded);
			}
			taxonNumber = lastTaxonNumber;


			Taxon t = taxa.getTaxon(taxonNumber);

			if (t!=null) {
				t.setName(sequenceName);
				if (progIndicator!=null) {
					progIndicator.setText("Reading taxon " + taxonNumber+": "+sequenceName);
					CommandRecord.tick("Reading taxon " + taxonNumber+": "+sequenceName);
					progIndicator.setCurrentValue(pos);
				}
				if (file!=null)
					line = file.readLine(">");  // pull in sequence up until next >
				else
					line = parser.getRemainingUntilChar('>', true);
				if (line==null) break;
				subParser.setString(line); 
				long s = 0;
				int tot = 0;
				int countA = 0;
				int countC = 0;
				int countG = 0;
				int countT = 0;
				int countAT = 0;
				int countCG=0;
				int length = 0;
				StringBuffer sequence = new StringBuffer();

				while (subParser.getPosition()<line.length()) {
					char c=subParser.nextDarkChar();
					if (c!= '\0') {
						sequence.append(c);
						length++;
						s = DNAState.fromCharStatic(c);
						if (!CategoricalState.isUnassigned(s) && !CategoricalState.isInapplicable(s)) {
							if (s == A || s == T || s == AT){ //monomorphic A or T or A&T or uncertain A or T
								tot++;
								countAT++;
							}
							else if (s == C || s == G || s == CG) { //monomorphic C or G or C&G or uncertain C or G
								tot++;
								countCG++;
							} else {
								Debugg.println("c: " + c);
							}
							if (s==A)
								countA++;
							else if (s==C)
								countC++;
							else if (s==G)
								countG++;
							else if (s==T)
								countT++;

						}

					}
				}
				data.setState(charLength, taxonNumber, 0, ((double)length));
				if (tot != 0) {
					data.setState(charA, taxonNumber, 0, ((double)countA)/tot);
					data.setState(charC, taxonNumber, 0, ((double)countC)/tot);
					data.setState(charG, taxonNumber, 0, ((double)countG)/tot);
					data.setState(charT, taxonNumber, 0, ((double)countT)/tot);
					data.setState(charAT, taxonNumber, 0, ((double)countAT)/tot);
					data.setState(charCG, taxonNumber, 0, ((double)countCG)/tot);
				}
				String sequenceNameSuffix = "";
				if (length>=lowerBlastSequenceLength && (blastOption==BLAST|| blastOption==BLASTX)){
					loglnEchoToStringBuffer("\nBLASTing  " + sequenceName, blastReport);
					loglnEchoToStringBuffer("   Sequence length: "+ length, blastReport);
					BLASTResults blastResult = new BLASTResults(numHits);
					if (blastOption==BLASTX)
						blasterTask.blastForMatches("blastx", sequenceName, sequence.toString(), true, numHits, 300, eValueCutoff, response, taxonNumber==1);
					else if (blastOption==BLAST)
						blasterTask.blastForMatches("blastn", sequenceName, sequence.toString(), true, numHits, 300, eValueCutoff, response,  taxonNumber==1);
					someBlastsDone = true;
					blastResult.processResultsFromBLAST(response.toString(), false, eValueCutoff);
					blasterTask.postProcessingCleanup(blastResult);

					if (blastOption==BLASTX)
						loglnEchoToStringBuffer("   BLASTX search completed", blastReport);
					else 
						loglnEchoToStringBuffer("   BLAST search completed", blastReport);
					if (blastResult.geteValue(0)<0.0) {
						loglnEchoToStringBuffer("   No hits.", blastReport);
					} else if (blastResult.geteValue(0)>eValueCutoff) {
						loglnEchoToStringBuffer("   No acceptable hits.", blastReport);
					} else {
						Associable associable = data.getTaxaInfo(true);
						associable.setAssociatedDouble(NCBIUtil.EVALUE, taxonNumber, blastResult.geteValue(0));
						associable.setAssociatedDouble(NCBIUtil.BITSCORE, taxonNumber, blastResult.getBitScore(0));
						associable.setAssociatedObject(NCBIUtil.DEFINITION, taxonNumber, blastResult.getDefinition(0));
						associable.setAssociatedObject(NCBIUtil.ACCESSION, taxonNumber, blastResult.getAccession(0));
						sequenceNameSuffix = " [top hit: " + StringUtil.getItem(blastResult.getDefinition(0), "|",5) + ", eValue=" + blastResult.geteValue(0) +  "]";

						fastaBLASTResults.setLength(0);
						// blastResult.processResultsFromBLAST(response.toString(), false, eValueCutoff);

						String[] IDs = blastResult.getIDs();
						if (IDs!=null) {
							loglnEchoToStringBuffer(blastResult.toString(numHits), blastReport);
						}

						if (storeBlastSequences) {
							if (blastOption==BLASTX){
								//blastResult.setIDFromDefinition("|", 2);
								IDs=blastResult.getIDs();
								IDs = blasterTask.getNucleotideIDsfromProteinIDs(IDs);
								// IDs = NCBIUtil.getNucIDsFromProtIDs(IDs);
							}

							String fasta = blasterTask.getFastaFromIDs(IDs,true, fastaBLASTResults);
							if (StringUtil.notEmpty(fasta)) {	
								//Debugg.println(blastResult.reversedToString());
								fastaBLASTResults.insert(0, ">"+sequenceName+"\n" + StringUtil.wrap(sequence.toString(), 60) + "\n");
								String fileName = "";

								saveInCriterionDirectory(blastResult, fastaBLASTResults.toString(), pathForBLASTfiles, sequenceName, 0);

							}

						}
						if (fetchTaxonomy) {
							String tax = blasterTask.getTaxonomyFromID(blastResult.getID(0), true, true, null);
							if (StringUtil.notEmpty(tax))
								associable.setAssociatedObject(NCBIUtil.TAXONOMY, taxonNumber, tax);
						}
					}
					accessionNumbers.deassignArray();


				}
				if (pathForResavedFile!=null) {
					String newSequenceName = sequenceName+sequenceNameSuffix;
					MesquiteFile.appendFileContents(pathForResavedFile, ">"+newSequenceName+"\n" + StringUtil.wrap(sequence.toString(), 60) + "\n", true);
				}

			}
			if (added) 
				lastTaxonNumber++;
			//			file.readLine(sb);
			if (file!=null) {
				line = file.readNextDarkLine();		// added 1.01
				pos = file.getFilePosition();
			}
			else {
				line = parser.getRawNextDarkLine();
				pos = parser.getPosition();
			}
			subParser.setString(line); //sets the string to be used by the parser to "line" and sets the pos to 0
			if (pathForBlastReport!=null)
				MesquiteFile.appendFileContents(pathForBlastReport, blastReport.toString(), true);
			blastReport.setLength(0);

			if (file !=null && file.getFileAborted()) {
				abort = true;
			}
		}
		if (lastTaxonNumber<taxa.getNumTaxa())
			if (data.hasDataForTaxa(lastTaxonNumber+1, taxa.getNumTaxa()-1))
				MesquiteMessage.discreetNotifyUser("Warning: InterpretFASTA attempted to delete extra taxa, but these contained data, and so were not deleted");
			else
				taxa.deleteTaxa(lastTaxonNumber, taxa.getNumTaxa()-lastTaxonNumber, true);   // add a character if needed
		if (numFilledChars<data.getNumChars())
			if (data.hasDataForCharacters(numFilledChars+1, data.getNumChars()-1))
				MesquiteMessage.discreetNotifyUser("Warning: InterpretFASTA attempted to delete extra characters, but these contained data, and so were not deleted");
			else
				data.deleteCharacters(numFilledChars+1, data.getNumChars()-numFilledChars, true);   // add a character if needed



		data.saveChangeHistory = wassave;
		data.resetChangedSinceSave();

		if (abort && AlertDialog.query(containerOfModule(), "Save partial file?" , "Save the portion that has been read in and processed?","Save", "Cancel"))
			abort=false;  // for this importer, save whatever you have read in
		finishImport(progIndicator, file, abort);

	}

	/*.................................................................................................................*/
	public void readFile(MesquiteProject mf, MesquiteFile file, String arguments) {
		if (blasterTask==null)
			blasterTask = (Blaster)hireEmployee(Blaster.class, "Blaster (for " + getName() + ")"); 
		if (blasterTask!=null)
			if (!blasterTask.initialize())
				return;

		if (!MesquiteThread.isScripting())
			if (!queryOptions()) 
				return;
		incrementMenuResetSuppression();
		ProgressIndicator progIndicator = new ProgressIndicator(mf,"Importing File "+ file.getName(), file.existingLength());
		progIndicator.start();
		file.linkProgressIndicator(progIndicator);
		if (file.openReading()) {
			logln("\nReading and processing file " + file.getName());
			if (blasterTask!=null && blastOption!=DONTBLAST)
				logln("  Blast uses \"" + blasterTask.getName() + "\"");
			TaxaManager taxaTask = (TaxaManager)findElementManager(Taxa.class);
			CharactersManager charTask = (CharactersManager)findElementManager(CharacterData.class);
			Taxa taxa = null;

			if (taxa == null){
				taxa = taxaTask.makeNewTaxa(getProject().getTaxas().getUniqueName("Taxa"), 0, false);
				taxa.addToFile(file, getProject(), taxaTask);
			}
			ContinuousData data = null;
			if (data == null){
				data =(ContinuousData)createData(charTask,taxa);
				data.addToFile(file, getProject(), null);
			}
			int numTaxa = 0;

			readFileCore(parser, file, data,  taxa, numTaxa, progIndicator, arguments);	
		}
		decrementMenuResetSuppression();
	}

	/*.................................................................................................................*/
	public String getName() {
		return "FASTA to Summary Stats, with BLAST (DNA)";
	}
	/*.................................................................................................................*/

	/** returns an explanation of what the module does.*/
	public String getExplanation() {
		return "Imports FASTA DNA files into a continuous matrix showing nucleotide frequencies, with the option to BLAST." ;
	}

	public boolean exportFile(MesquiteFile file, String arguments) {
		return false;
	}



}

