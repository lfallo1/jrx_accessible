package com.cozcompany.jrx.accessibility.utilities;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileHelpers {
    public String findAbsolutePath( String curl ){
        String path = System.getenv( "PATH" );
        System.out.println("System.getenv(PATH) returns :" + path);
        String[] dirs = path.split( ";|:" );
        
//        ////////////////////////////////////////////////////BEGIN HACK
//        // HACK - DO NOT RELEASE THIS CODE.
//        ///////////////////////////////        
//        Path aCurl = Paths.get(  "/user/local/bin", curl );
//        String commandString = aCurl.toAbsolutePath().toString();
//        File aFile = new File( aCurl.toString() ); 
//        if(aFile.canExecute() ) return aCurl.toAbsolutePath().toString();        
//        System.out.println("command : "+ commandString);
//        ///////////////////////////////////////////////////END HACK
        
        File rigctldFile = new File("/usr/local/bin/rigctld");
        if ( rigctldFile.canExecute()) {
            System.out.println("can Execute() : "+ rigctldFile.toString());
            return rigctldFile.toString();
        }




        for( String dir: dirs ){
            Path toCurl = Paths.get( dir, curl );
            File curlFile = new File( toCurl.toString() );           
            if( curlFile.canExecute() ) {
                System.out.println("can Execute() : "+ toCurl.toAbsolutePath().toString());          
                return toCurl.toAbsolutePath().toString();
            }
        }
        return null; /// THIS SHOULD BE return null;
    }
}
