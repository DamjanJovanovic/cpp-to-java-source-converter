package com.github.danfickle.cpptojavasourceconverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.gnu.cpp.GPPLanguage;
import org.eclipse.cdt.core.index.IIndexFileLocation;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.DefaultLogService;
import org.eclipse.cdt.core.parser.FileContent;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.internal.core.parser.IMacroDictionary;
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContent;
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContentProvider;

public class Main
{
//	final static String HOME_PATH = "/home/daniel/workspace/cpp-to-java-source-converter/";
	
	
//	public static void main(String... args) throws Exception
//	{
//		GlobalCtx global = new GlobalCtx();
//		
//		BufferedReader br = new BufferedReader(new FileReader(HOME_PATH + "tests/" + "list-of-test-files.txt"));
//	    String line = br.readLine();
//
//	    while (line != null) {
//	    	if (!line.isEmpty() && !line.startsWith("#"))
//	    	{
//	    		IASTTranslationUnit tu = getTranslationUnit(HOME_PATH + "tests/" + line + ".cpp");
//	    		Traverser parser = new Traverser();
//	    		String outputCode = parser.traverse(tu, global);
//	    		
//	    		FileOutputStream fos = new FileOutputStream(HOME_PATH + "crap/" + line + ".java");
//	    		OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8"); 
//	    		out.write(outputCode);
//	    		out.close();
//	    	}
//	    	line = br.readLine();
//	    }
//	    br.close();
//	}

    public static void main(String... args) throws Exception {
        ArrayList<String> includePaths = new ArrayList<>();
        Map<String,String> defines = new TreeMap<>();
        includePaths.add(new File(".").getAbsoluteFile().getParentFile().getAbsolutePath());
        String inputFile = null;
        String outputFile = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-I")) {
                includePaths.add(args[i].substring(2));
            } else if (args[i].startsWith("-D")) {
                final String value = args[i].substring(2);
                final int equals = value.indexOf('=');
                if (equals >= 0) {
                    defines.put(value.substring(0, equals), value.substring(equals + 1));
                } else {
                    defines.put(value, "");
                }
            } else if (i > 0 && args[i - 1].equals("-o")) {
                outputFile = args[i];
            } else if (args[i].equals("-o")) {
            } else {
                if (inputFile == null) {
                    inputFile = args[i];
                } else {
                    throw new Exception("Input file given multiple times");
                }
            }
        }
        if (inputFile == null) {
            throw new Exception("No input file given");
        }
        if (outputFile == null) {
            throw new Exception("No output file given");
        }
        
        for (String path : includePaths) {
            System.out.println("PATH: " + path);
        }
        
        GlobalCtx global = new GlobalCtx();
        IASTTranslationUnit tu = getTranslationUnit(inputFile, defines, includePaths.toArray(new String[includePaths.size()]));
        Traverser parser = new Traverser();
        String outputCode = parser.traverse(tu, global);
      
        FileOutputStream fos = new FileOutputStream(outputFile);
        OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
        out.write(outputCode);
        out.close();
    }

	private static IASTTranslationUnit getTranslationUnit(String filename, Map<String,String> defines, String[] includePaths) throws Exception
	{
		IParserLogService log = new DefaultLogService();
		FileContent ct = FileContent.createForExternalFileLocation(filename);
		return GPPLanguage.getDefault().getASTTranslationUnit(ct, new Scanner(defines, includePaths),
		        new FileProvider(), null, ILanguage.OPTION_IS_SOURCE_UNIT, log);
	}
	
	private static class FileProvider extends InternalFileContentProvider
	{
		@Override
		public InternalFileContent getContentForInclusion(String path,
				IMacroDictionary macroDictionary) {
			return (InternalFileContent) InternalFileContent.createForExternalFileLocation(path);
		}

		@Override
		public InternalFileContent getContentForInclusion(
				IIndexFileLocation arg0, String arg1) {
			return null;
		}
	}

	
	private static class Scanner implements IScannerInfo
	{
	    private final Map<String,String> defines;
	    private final String[] includePaths;
	    
	    public Scanner(Map<String,String> defines, String[] includePaths) {
	        this.defines = defines;
	        this.includePaths = includePaths;
	    }
	    
		@Override
		public Map<String, String> getDefinedSymbols() {
			return defines;
		}		
		
		@Override
		public String[] getIncludePaths() {
		    return includePaths;
		}
	}
	
}
