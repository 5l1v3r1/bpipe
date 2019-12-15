/*
 * Copyright (c) 2016 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe.cmd

import java.io.Writer;

import java.util.List
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import javax.swing.colorchooser.CenterLayout

import org.apache.ivy.util.FileUtil

import bpipe.CommandManager;
import bpipe.Config
import bpipe.ExecutorFactory
import bpipe.ExecutorPool
import bpipe.PooledExecutor
import bpipe.Runner
import bpipe.Utils
import groovy.io.FileType
import groovy.transform.CompileStatic;
import groovy.util.logging.Log;

/**
 * Clean up all the temporary files and commands generated by the pipeline into an archive zip file
 *   
 * @author Simon Sadedin
 */
@Log
class ArchiveCommand extends BpipeCommand {
    
    public ArchiveCommand(List<String> args) {
        super("archive", args);
    }

    @Override
    public void run(Writer out) {
        parse('archive <zip file>') {
            d 'Remove archived files', longOpt:'delete'
        }

        String zipPath = opts.arguments()[0]
        if(!zipPath)
            throw new IllegalArgumentException("Please provide the path to a zip file to archive into")
        
        if(!zipPath.endsWith('.zip')) {
            new File(zipPath).mkdirs()
            zipPath = computeZipPath(zipPath)
            println "MSG: Archiving to computed file path ${zipPath}"
        }

        if(!zipPath.endsWith('.zip')) 
            throw new IllegalArgumentException("The archive file name should end with .zip")

        List archivedFiles 
        new File(zipPath).withOutputStream { 
            archivedFiles = createArchiveZip(new ZipOutputStream(it))
        }
        
        out.println "MSG: Archived ${archivedFiles.size()} files"
        List<File> dirs = []
        if(opts.d) {
            for(File f in archivedFiles) {
                if(!f.delete()) {
                    out.println "WARNING: could not delete $f"
                }
                if(f.parentFile)
                    dirs << f.parentFile
            }
            
            System.addShutdownHook { 
                new File('.bpipe').deleteDir()
            }
        }
    }
    
    /**
     * Archive the local bpipe commands into the given archive file and return
     * the list of archived files
     * @param zos
     * @return
     */
    @CompileStatic
    List<File> createArchiveZip(ZipOutputStream zos) {
        List<File> toDelete = []
        new File('.bpipe').eachFileRecurse(FileType.FILES) { File f ->
            zos.putNextEntry(new ZipEntry(f.path))
            f.withInputStream { zos << it }
            zos.closeEntry()
            toDelete << f
        }
        File commandlog = new File("commandlog.txt")
        zos.putNextEntry(new ZipEntry(commandlog.path))
        zos.write(commandlog.readBytes())
        zos.close()
        toDelete << commandlog
    }
    
    String computeZipPath(String dir) {
        Date now = new Date()
        return new File(dir, "bpipe_archive_" + now.format('YMMdd_HHmmss') + '.zip').path
    }
}
