import javax.swing.*;
import java.util.*;
import java.io.*;

public class Comp {

    static String CONFIG_FILE = "ccompiler_config.cfg";
    static String CACHE_DIRECTORY = "ccompiler_cache";
    static String CACHE_REGISTRY = CACHE_DIRECTORY + "/cache_registry.dat";
    static String CONFIG_CACHE = CACHE_DIRECTORY + "/config_registry.txt";
    static ArrayList<String> configuration_lines = new ArrayList<>();
    static ArrayList<String> compiler_options = new ArrayList<>();
    static ArrayList<String> compiling_extensions = new ArrayList<>();
    static ArrayList<String> include_path = new ArrayList<>();
    static ArrayList<String> libraries_path = new ArrayList<>();
    static ArrayList<String> libraries = new ArrayList<>();
    static String OutputName = "a.out";

    static boolean ForceRecompile = false;

    // filename, last modified
    static HashMap<String, Long> REG = new HashMap<>();

    public static void main(String[] args) throws Exception {
        File configfile = new File(CONFIG_FILE);
        if(!configfile.exists())
            FirstTimeUse();
        File cachedir = new File(CACHE_DIRECTORY);
        if(!cachedir.exists())
            if(!cachedir.mkdir()) {
                System.err.println("Could not create cache directory '" + CACHE_DIRECTORY + "' Fatal Error!");
                System.exit(4);
            }
        if(new File(CONFIG_CACHE).exists()) {
            ArrayList<String> configreg = LoadFileStrings(CONFIG_CACHE);
            if (configreg.size() == 0) {
                ForceRecompile = true;
            } else {
                try {
                    long last_modified = Long.parseLong(configreg.get(0));
                    if (last_modified != (new File(CONFIG_FILE)).lastModified()) {
                        ForceRecompile = true;
                    }
                } catch (Exception e) {
                    ForceRecompile = true;
                }
            }
        } else {
            ForceRecompile = true;
        }
        if(ForceRecompile) {
            System.out.println("================FORCING RECOMPILE================");
            File[] cache_files = new File(CACHE_DIRECTORY).listFiles();
            if(cache_files != null)
                for(var cf : cache_files) {
                    cf.delete();
                }
            File i = new File(CONFIG_CACHE);
            i.createNewFile();
            BufferedWriter output = new BufferedWriter(new FileWriter(i));
            output.write(Long.toString(new File(CONFIG_FILE).lastModified()));
            output.close();
        }
        configuration_lines = LoadFileStrings(CONFIG_FILE);
        LoadConfigurations();
        LoadCacheRegistry();
        Compile();
    }

    static void Compile() throws Exception {
        // TODO: Support multithreading compiling.
        System.out.println("Compiling...");
        String working_directory = System.getProperty("user.dir");
        var files = LoadFiles();
        if(files.size() == 0) {
            System.err.println("Could not find any files to compile!");
            System.exit(5);
        }
        var CacheEntries = LoadFileStrings(CACHE_REGISTRY);
        for(var file : files) {
            long last_access = REG.getOrDefault(file.getName(), 0L);
            if(last_access == 0) {
                int exitcode = CompileHelperFunction(working_directory, file);
                Thread.sleep(50);
                // Update Registry File
                // Add New Entry
                if(exitcode == 1)
                    CacheEntries.add(file.getName() + ":" + file.lastModified());
            } else if(last_access != file.lastModified()) {
                // compile again because file changed!
                int exitcode = CompileHelperFunction(working_directory, file);
                Thread.sleep(50);
                // Update Registry File
                // 1) Remove Old Entry
                for(int i = 0; i < CacheEntries.size(); i++) {
                    if(CacheEntries.get(i).startsWith(file.getName())) {
                        CacheEntries.remove(i);
                    }
                }
                // 2) Add New Entry
                if(exitcode == 1)
                    CacheEntries.add(file.getName() + ":" + file.lastModified());
            }
        }
        // link functions
        ArrayList<String> command = new ArrayList<>();
        command.add("g++");
        command.addAll(compiler_options);
        File cd = new File(CACHE_DIRECTORY);
        var cdfiles = cd.listFiles();
        if(cdfiles == null) {
            JOptionPane.showMessageDialog(null, "Cannot link program because no object files were found!", "Fatal Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        for(var temp : cdfiles) {
            if(temp.isFile())
                if(temp.getName().endsWith(".o"))
                    command.add(CACHE_DIRECTORY + "/" + temp.getName());
        }
        command.add("-o");
        command.add(OutputName);
        for (String s : libraries_path) {
            command.add("-L" + s);
        }
        for (String temp : libraries) {
            if (!temp.startsWith("-l"))
                temp = "-l" + temp;
            command.add(temp);
        }
        System.out.println("=========================LINKING=========================");
        ProcessBuilder link = new ProcessBuilder(command);
        link.inheritIO();
        var a = link.command();
        String[] b = new String[a.size()];
        a.toArray(b);
        System.out.println(Arrays.toString(b).replace("[", "").replaceAll(",", "").replace("]", ""));
        link.start();
        // Save new cache
        BufferedWriter output = new BufferedWriter(new FileWriter(CACHE_REGISTRY));
        for(var entry : CacheEntries)
            output.write(entry + "\n");
        output.close();
    }

    static int CompileHelperFunction(String working_directory, File file) throws Exception {
        // file was not compiled
        ArrayList<String> command = new ArrayList<>();
        command.add("g++");
        command.addAll(compiler_options);
        for (String s : include_path) {
            command.add("-I" + s);
        }
        command.add("-c");
        command.add(file.getAbsolutePath());
        String output_location = working_directory + "/" + CACHE_DIRECTORY + "/" + file.getName().substring(0, file.getName().lastIndexOf(("."))) + ".o";
        command.add("-o");
        command.add(output_location);
        System.out.println(file.getName());
        ProcessBuilder process = new ProcessBuilder(command);
        process.inheritIO();
        Process p = process.start();
        p.waitFor();
        return p.exitValue();
    }

    static ArrayList<File> LoadFiles() {
        String working_directory = System.getProperty("user.dir");
        System.out.println("Working Directory: " + working_directory);
        File wd = new File(working_directory);
        ArrayList<File> subobjects = new ArrayList<>();
        LoadFilesHelper(wd, subobjects);
        return subobjects;
    }

    static void LoadFilesHelper(File dir, ArrayList<File> SubObjects) {
        var list_obj = dir.listFiles();
        assert list_obj != null;
        for(var o : list_obj) {
            if(o.isFile()) {
                // filter with compiling extensions
                String abs_path = o.getAbsolutePath();
                if(abs_path.contains("CMake"))
                    continue;
                for(var ext : compiling_extensions) {
                    if(!ext.startsWith("."))
                        ext = "." + ext;
                    if(abs_path.toUpperCase().endsWith(ext.toUpperCase())) {
                        SubObjects.add(o);
                    }
                }
            }
            else
                LoadFilesHelper(o, SubObjects);
        }
    }

    static void LoadCacheRegistry() throws Exception {
        File registryfile = new File(CACHE_REGISTRY);
        if(!registryfile.exists()) {
            System.out.println("Creating Cache Registry");
            registryfile.createNewFile();
            return;
        }
        ArrayList<String> registry = LoadFileStrings(CACHE_REGISTRY);
        // filename:last_modified
        for(String reg : registry) {
            String[] reg_split = reg.split(":");
            if(reg_split.length != 2) {
                System.err.println("Registry has been corrupted! Please deleted to let compiler create a new registry.");
                System.exit(0);
            }
            REG.put(reg_split[0], Long.parseLong(reg_split[1]));
        }
    }

    static void LoadConfigurations() {
        for(ReferenceInteger i = new ReferenceInteger(); i.val < configuration_lines.size(); i.val++) {
            if(i.val == configuration_lines.size() - 1)
                break;
            String line = configuration_lines.get(i.val);
            if(line.startsWith("#"))
                continue;
            if(line.startsWith("[options]")) {
                compiler_options = HelperConfigurationFunction(i);
            }
            else if(line.startsWith("[ext]")) {
                compiling_extensions = HelperConfigurationFunction(i);
            } else if(line.startsWith("[include]")) {
                include_path = HelperConfigurationFunction(i);
            } else if(line.startsWith("[libpath]")) {
                libraries_path = HelperConfigurationFunction(i);
            } else if(line.startsWith("[lib]")) {
                libraries = HelperConfigurationFunction(i);
            } else if(line.startsWith("output_name")) {
                OutputName = line.substring(line.indexOf("=") + 1);
                if(OutputName.length() == 0)
                    OutputName = "a.out";
                System.out.println("output_name=" + OutputName);
            } else {
                // error
                System.err.println("INVALID CONFIGURATION! At line " + (i.val + 1));
                System.err.println(line);
                System.exit(2);
            }
        }
        if(compiling_extensions.size() == 0) {
            System.err.println("Cannot compile with 0 compiling extensions! Please fix it.");
            System.exit(3);
        }
        System.out.println("===================Compiling Information===================");
        String[] options_arr = new String[compiler_options.size()];
        String[] compiling_extensions_arr = new String[compiling_extensions.size()];
        String[] include_arr = new String[include_path.size()];
        String[] libpath_extensions_arr = new String[libraries_path.size()];
        String[] lib_extensions_arr = new String[libraries.size()];
        compiler_options.toArray(options_arr);
        compiling_extensions.toArray(compiling_extensions_arr);
        include_path.toArray(include_arr);
        libraries_path.toArray(libpath_extensions_arr);
        libraries.toArray(lib_extensions_arr);
        System.out.println("Compiler Options: " + Arrays.toString(options_arr));
        System.out.println("Compiling Extensions: " + Arrays.toString(compiling_extensions_arr));
        System.out.println("Include Path: " + Arrays.toString(include_arr));
        System.out.println("Libraries Path: " + Arrays.toString(libpath_extensions_arr));
        System.out.println("Libraries: " + Arrays.toString(lib_extensions_arr));
        System.out.println("===========================================================");
    }

    static ArrayList<String> HelperConfigurationFunction(ReferenceInteger index) {
        ArrayList<String> result = new ArrayList<>();
        String ext_line;
        do {
            if(index.val == configuration_lines.size() - 1) {
                ext_line = null;
                continue;
            }
            ext_line = configuration_lines.get(++index.val);
            if(ext_line.startsWith("["))
                break;
            if(ext_line.startsWith("#"))
                continue;
            if(ext_line.length() == 0)
                continue;
            if(ext_line.startsWith("-l")) {
                ext_line = ext_line.replace("-l", "");
            }
            result.add(ext_line);
        } while(ext_line != null);
        index.val--;
        return result;
    }

    static ArrayList<String> LoadFileStrings(String path) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(path));
        String tmp;
        ArrayList<String> result = new ArrayList<>();
        while((tmp = input.readLine()) != null) {
            result.add(tmp.trim());
        }
        input.close();
        return result;
    }

    static void FirstTimeUse() throws Exception {
        File configfile = new File(CONFIG_FILE);
        if(!configfile.createNewFile()) {
            System.err.println("Could not create config file, for some reason! Fatal error.");
            System.exit(1);
        }
        System.out.println("First time use! Open '" + CONFIG_FILE + "' and set properties!");
        BufferedWriter out = new BufferedWriter(new FileWriter(configfile));
        out.write("# This is a comment; Note (// are not comments)\n");
        out.write("# The following determines the name of the compiled program (eg. g++ main.c -o MyProgram)\n");
        out.write("output_name=a.out\n");
        out.write("# The following are compiler options (example: -std=gnu++2a)\n");
        out.write("[options]\n");
        out.write("# The following extensions will be compiled\n");
        out.write("[ext]\n");
        out.write("c\n");
        out.write("cpp\n");
        out.write("# The following specifies include paths (relative to the .jar file)\n");
        out.write("[include]\n");
        out.write("# The following specifies where the libraries are located (relative to the .jar file)\n");
        out.write("[libpath]\n");
        out.write("# The following specifies libraries used (relative to the .jar file)\n");
        out.write("[lib]\n");
        out.close();
        System.exit(0);
    }

    static class ReferenceInteger {
        public int val = 0;
    }

}
