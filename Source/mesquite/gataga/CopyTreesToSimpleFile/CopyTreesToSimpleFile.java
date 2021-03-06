/* Mesquite source code.  Copyright 1997-2011 W. Maddison and D. Maddison.

Version 2.75, September 2011.
Disclaimer:  The Mesquite source code is lengthy and we are few.  There are no doubt inefficiencies and goofs in this code. 
The commenting leaves much to be desired. Please approach this source code with the spirit of helping out.
Perhaps with your help we can be more than a few, and make Mesquite better.

Mesquite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
Mesquite's web site is http://mesquiteproject.org

This source code and its compiled class files are free and modifiable under the terms of 
GNU Lesser General Public License.  (http://www.gnu.org/copyleft/lesser.html)
 */
package mesquite.gataga.CopyTreesToSimpleFile; 

import java.awt.FileDialog;

import mesquite.lib.*;
import mesquite.lib.characters.CharacterData;
import mesquite.lib.duties.*;

/* ======================================================================== */
public class CopyTreesToSimpleFile extends FileAlterer {
	String saveFile = null;
	/*.................................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		return true;
	}
	/*.................................................................................................................*/
	/** returns whether this module is requesting to appear as a primary choice */
	public boolean requestPrimaryChoice(){
		return false;  
	}
	/*.................................................................................................................*/
	public boolean isPrerelease(){
		return true;
	}
	/*.................................................................................................................*/
	public boolean isSubstantive(){
		return true; //not really, but to force checking of prerelease
	}

	/** if returns true, then requests to remain on even after alterFile is called.  Default is false*/
	public boolean pleaseLeaveMeOn(){
		return false;
	}
	/*.................................................................................................................*/
	/** Called to alter file. */
	public boolean alterFile(MesquiteFile file){
		if (saveFile == null || okToInteractWithUser(CAN_PROCEED_ANYWAY, "Asking for file to save")){ //need to check if can proceed
			
			MesquiteFileDialog fdlg= new MesquiteFileDialog(containerOfModule(), "Output File for Tree(s)", FileDialog.SAVE);
			fdlg.setBackground(ColorTheme.getInterfaceBackground());
			fdlg.setVisible(true);
			String fileName=fdlg.getFile();
			String directory=fdlg.getDirectory();
			// fdlg.dispose();
			if (StringUtil.blank(fileName) || StringUtil.blank(directory))
				return false;
			saveFile = MesquiteFile.composePath(directory, fileName);
		}
		if (saveFile == null)
			return false;
		Listable[] treeVectors = proj.getFileElements(TreeVector.class);	
   		if (treeVectors == null)
   			return false;
		for (int im = 0; im < treeVectors.length; im++){
   			TreeVector trees = (TreeVector)treeVectors[im];
   			if (trees.getFile() == file){
   				for (int itree = 0; itree < trees.size(); itree++){
   					Tree t = trees.getTree(itree);
   					String description = t.writeTree(Tree.BY_NAMES);
   					MesquiteFile.appendFileContents(saveFile, description + StringUtil.lineEnding(), true);
  				}
   			}
   		}
		return true;

	}
	/*.................................................................................................................*/
	public String getName() {
		return "Put Trees into Simple File";
	}
	/*.................................................................................................................*/
	/** returns an explanation of what the module does.*/
	public String getExplanation() {
		return "Puts trees from this file into a simple text file." ;
	}

}


