package com.cozcompany.jrx.accessibility.utilities;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileHelpers {
    public String findAbsolutePath( String curl ){
        String path = System.getenv( "PATH" );
        String[] dirs = path.split( ";|:" );
        for( String dir: dirs ){
            Path toCurl = Paths.get( dir, curl );
            File curlFile = new File( toCurl.toString() );
            if( curlFile.canExecute() ) return toCurl.toAbsolutePath().toString();
        }
        return null;
    }
}
