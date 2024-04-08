package com.github.tricksteronline;/*
Analyzer.java: this file is part of the TNT program.

Copyright (C) 2014-2020 Libre Trickster Team

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/
import java.nio.*;
import static java.lang.System.out;
/**
Class Description:
The Analyzer class contains all functions that TNT uses to find the information
that it needs to know about a NORI file in order to perform its duties.

Dev Notes:
It is assumed that you have read the NORI Format Specification first, so most of
the variables and variable assignments are not commented. Descriptive names are
used for most things regardless. Fair warning has been given.

Development Priority: HIGH
*/
public class Analyzer {
    // class variables
    public static int pos=0,rem=0,bpos=0,apos=0,fpos=0,bmpNxt=0,animNxt=0;
    // special GAWI variables
    public static int gStart=0,gEnd=0;
    public static boolean compressed=false, hasPalette=false;
    // special BMP data variables
    public static int[] bmpOffsets;
    // special animation variables
    public static int asize=0, numFrames=0, numPlanes=0;
    public static int[] animOffsets;
    public static byte[] animName = new byte[32];
    public static int[][] frameOffsets;

    // constructor for Analyzer class
    public Analyzer(ByteBuffer bb) {
        out.println("========================================================");
        out.println("Filename: " + NORI.name);
        try {
            // Read and Assign info about the noriFile
            setNoriHeader(bb);
            setGawiHeader(bb);
            if (hasPalette) setPaletteData(bb);
            setBmpOffsets(bb);
            dryExtract(bb);// Skip through bmpData, assign bmpSpecs data
            if (NORI.gsize == 0) gawiSizeFixes();
            prepAnimVars();
            setAnimOffsets(bb);
            setAnimInfo(bb);
            offsetCheck();
            //bbStatus(bb);// rem!=0 if noriVer is wrong (ex: Mini_mapd01a.nri)
            // Reset bytebuffer for extraction
            bb.position(bpos);
        } catch(Exception ex) {
            out.println("Error in (AM):\n"+ex);
        }
    }

    private static void setNoriHeader(ByteBuffer bb) {
        NORI.fsig = bb.getInt();
        noriCheck(NORI.fsig);
        NORI.noriVer = bb.getInt();
        noriVerCheck();
        NORI.nParam1 = bb.getInt();
        NORI.nParam2 = bb.getInt();
        NORI.nParam3 = bb.getInt();
        NORI.nParam4 = bb.getInt();
        NORI.nParam5 = bb.getInt();
        NORI.anims = bb.getInt();
        out.println("# of animations: " + NORI.anims);
        NORI.woGawi = bb.getInt();
        out.println("fsize w/o GAWI: " + NORI.woGawi);
        NORI.fsize = bb.getInt();
        out.println("fsize: " + NORI.fsize);
        if (NORI.fsize == 0) NORI.fsize = bb.capacity();// Fix Ntree*'s mistake
        out.println();
    }

    private static void setGawiHeader(ByteBuffer bb) {
        gStart = bb.position();
        NORI.gsig = bb.getInt();
        gawiCheck(NORI.gsig);
        NORI.gawiVer = bb.getInt();
        gawiVerCheck(NORI.gawiVer);
        NORI.bpp = bb.getInt();
        out.println("BitsPerPixel: "+ NORI.bpp);
        NORI.compressed = bb.getInt();
        compressed = (NORI.compressed ==1);
        out.println("Compressed: "+compressed);
        NORI.hasPalette = bb.getInt();
        hasPalette = (NORI.hasPalette ==1);
        out.println("hasPalette: "+hasPalette);
        NORI.gParam4 = bb.getInt();
        NORI.gParam5 = bb.getInt();
        NORI.gParam6 = bb.getInt();
        NORI.gParam7 = bb.getInt();
        NORI.numBMP = bb.getInt();
        out.println("# of images: "+ NORI.numBMP);
        NORI.gsize = bb.getInt();
        out.println("gsize: "+ NORI.gsize);
        out.println();
    }

    private static void setPaletteData(ByteBuffer bb) {
        NORI.psig = bb.getInt();
        palCheck(NORI.psig);
        NORI.palVer = bb.getInt();
        palVerCheck(NORI.palVer);
        NORI.pParam1 = bb.getInt();
        NORI.pParam2 = bb.getInt();
        NORI.pParam3 = bb.getInt();
        NORI.pParam4 = bb.getInt();
        NORI.divided = bb.getInt();
        NORI.psize = bb.getInt();
        out.println("psize: "+ NORI.psize);
        NORI.palette = setPalette(bb);
        if (NORI.psize == 808) {
            NORI.mainS = bb.getInt();
            NORI.mainE = bb.getInt();
        }
        out.println();
    }

    // Make BMP color palette from raw palette data. Okay, one of the harder to
    // follow parts here. Colors are stored in BGR order. Take it in stride.
    private static byte[][] setPalette(ByteBuffer bb) {
        NORI.pb = new byte[768];
        byte[] newBG = {(byte)255,(byte)0,(byte)255};
        byte[][] colors = new byte[256][3];
        try {
            // gets/puts the palette bytes into the pb array
            bb.get(NORI.pb,0,768);
            ByteBuffer pbb = mkLEBB(NORI.pb);
            // standardize the bg to neon pink
            pbb.put(newBG,0,3);
            // Place the bytes in the dual array 'colors' that groups the rgb
            // bytes according to the color/palette index they represent
            for(int i = 0; i < 256; i++) {
                int x = i*3, b=x+0, g=x+1, r=x+2;
                colors[i][0] = NORI.pb[r];
                colors[i][1] = NORI.pb[g];
                colors[i][2] = NORI.pb[b];
            }
        }
        catch(Exception ex) {
            out.println("Error in (setPal):\n"+ex);
        }
        return colors;
    }

    // Load bmp offsets into the bmpOffsets array for global use
    private static void setBmpOffsets(ByteBuffer bb)
    {
        bmpOffsets = new int[NORI.numBMP +1];
        NORI.bmpOffsets = new int[NORI.numBMP +1];
        for(int i = 0; i < NORI.numBMP; i++)
        {
            NORI.bmpOffsets[i] = bb.getInt();
            if(compressed) NORI.bmpOffsets[i] += i*28;
        }
        // get buffer position at end of offsets
        bpos = bb.position();
        NORI.bpos = bpos;
    }

    // Load the bmpSpecs array and simulate extraction for the bytebuffer
    private static void dryExtract(ByteBuffer bb) {
        NORI.bmpSpecs = new int[NORI.numBMP][7];
        int offsetDiff = NORI.bmpOffsets[NORI.numBMP -1]- NORI.bmpOffsets[0];
        boolean offDiff = (offsetDiff > 0);
        for(int i = 0; i < NORI.numBMP; i++)
        {
            bmpOffsets[i] = bb.position() - bpos;// Set offsetCheck() value
            if(NORI.bmpOffsets[i+1]!=0)
                bmpNxt= NORI.bmpOffsets[i+1] + bpos;
            else
                bmpNxt= NORI.bmpOffsets[i+1];
            NORI.bmpSpecs[i][0] = bb.getInt();
            NORI.bmpSpecs[i][1] = bb.getInt();
            NORI.bmpSpecs[i][2] = bb.getInt();
            NORI.bmpSpecs[i][3] = bb.getInt();
            NORI.bmpSpecs[i][4] = bb.getInt();
            NORI.bmpSpecs[i][5] = bb.getInt();
            NORI.bmpSpecs[i][6] = bb.getInt();
            JBL.setBmpVars(NORI.bmpSpecs[i][2], NORI.bmpSpecs[i][3], NORI.bpp);
            byte[] rawBytes = JBL.getImgBytes(bb, NORI.bmpSpecs[i][1]);
            // Ensure the buffer is in the right position for the next bmp
            if(offDiff && bpos!=bmpNxt && bmpNxt!=0) bb.position(bmpNxt);
        }
        gEnd = bb.position();
        asize = bb.remaining();
    }

    // One of many data fixes I've implemented to prevent Ntree* mistakes from
    // being carried over to the config files. This fixes woGawi and gsize.
    private static void gawiSizeFixes() {
        NORI.gsize = gEnd - gStart;
        if (NORI.woGawi == 0) NORI.woGawi = 40 + asize;
    }

    // Prepare the animation-related arrays
    private static void prepAnimVars() {
        // large array sizes needed for dealing with unknown input
        animOffsets = new int[NORI.anims +1];
        NORI.animOffsets = new int[NORI.anims +1];
        NORI.animName = new String[NORI.anims];
        NORI.frames = new int[NORI.anims];
        frameOffsets = new int[NORI.anims][200];
        NORI.frameOffsets = new int[NORI.anims][200];
        NORI.frameData = new int[NORI.anims][200][2];
        NORI.planeData = new int[NORI.anims][200][100][7];
        NORI.xfb = new byte[NORI.xtraFrameBytes];
    }

    private static void setAnimOffsets(ByteBuffer bb) {
        for (int i = 0; i < NORI.anims; i++) {
            NORI.animOffsets[i] = bb.getInt();
        }
        apos = bb.position();
        NORI.apos = apos;
    }

    // Set the info for all the animations
    private static void setAnimInfo(ByteBuffer bb) {
        try {
            int offsetDiff = NORI.animOffsets[NORI.anims -1] - NORI.animOffsets[0];
            boolean offDiff = (offsetDiff > 0);
            for (int i = 0; i < NORI.anims; i++) {
                animOffsets[i] = bb.position() - apos;// Set offsetCheck() value
                if (NORI.animOffsets[i+1]!=0) {
                    animNxt = NORI.animOffsets[i+1] + apos;
                } else {
                    animNxt = NORI.animOffsets[i+1];
                }
                bb.get(animName,0,32);
                NORI.animName[i] = (new String(animName,"EUC-KR")).trim();
                NORI.frames[i] = bb.getInt();
                numFrames = NORI.frames[i];
                setFrameOffsets(bb,i);
                setFrameData(bb,i);
                pos = bb.position();
                if (offDiff && pos!=animNxt && animNxt!=0) bb.position(animNxt);
            }
        }
        catch(Exception ex) {
            out.println("Error in (setAnimInfo):\n"+ex);
        }
    }

    private static void setFrameOffsets(ByteBuffer bb, int a) {
        for (int i=0; i < numFrames; i++) {
            NORI.frameOffsets[a][i] = bb.getInt();
        }
        fpos = bb.position();
    }

    // Set actual data for frames (and planes)
    private static void setFrameData(ByteBuffer bb, int a) {
        for (int i=0; i < numFrames; i++) {
            frameOffsets[a][i] = bb.position() - fpos;// Set offsetCheck() value
            NORI.frameData[a][i][0] = bb.getInt();
            NORI.frameData[a][i][1] = bb.getInt();
            numPlanes = NORI.frameData[a][i][1];
            setPlaneData(bb,a,i);
        }
    }

    private static void setPlaneData(ByteBuffer bb, int a, int f) {
        for (int i=0; i < numPlanes; i++) {
            NORI.planeData[a][f][i][0] = bb.getInt();
            NORI.planeData[a][f][i][1] = bb.getInt();
            NORI.planeData[a][f][i][2] = bb.getInt();
            NORI.planeData[a][f][i][3] = bb.getInt();
            NORI.planeData[a][f][i][4] = bb.getInt();
            NORI.planeData[a][f][i][5] = bb.getInt();
            NORI.planeData[a][f][i][6] = bb.getInt();
        }

        bb.get(NORI.xfb,0, NORI.xtraFrameBytes);
    }

    // Check if nf offset arrays = local arrays, fix nf arrays if not equal
    private static void offsetCheck() {
        if (NORI.bmpOffsets != bmpOffsets)
            NORI.bmpOffsets = bmpOffsets;

        if (NORI.animOffsets != animOffsets)
            NORI.animOffsets = animOffsets;

        if (NORI.frameOffsets != frameOffsets)
            NORI.frameOffsets = frameOffsets;
    }

    private static void noriCheck(int signature) {
        out.print("NORI Signature Check: ");
        intCheck(1230131022, signature);
    }

    // Checks NORI version and sets the appropriate # of extra bytes
    private static void noriVerCheck() {
        out.print("NORI Version: ");

        switch(NORI.noriVer) {
            case 300:
                NORI.xtraFrameBytes = 224;
                break;
            case 301:
                NORI.xtraFrameBytes = 228;
                break;
            case 302:
                NORI.xtraFrameBytes = 348;
                break;
            case 303:
                NORI.xtraFrameBytes = 352;
                break;
            default:
                out.println("Unknown type! File a bug report.");
                System.exit(1);
                break;
        }

        out.println(NORI.noriVer);
    }

    private static void gawiCheck(int signature) {
        out.print("GAWI Signature Check: ");
        intCheck(1230455111, signature);
    }

    private static void gawiVerCheck(int verNum) {
        out.print("GAWI Version: ");
        intCheck(300,verNum);
    }

    private static void palCheck(int signature) {
        out.print("PAL_ Signature Check: ");
        intCheck(1598832976, signature);
    }

    private static void palVerCheck(int verNum) {
        out.print("PAL_ Version: ");
        intCheck(100,verNum);
    }

    // Reusable int check, b/c we do this often
    private static void intCheck(int ref, int input) {
        if (input == ref) {
            out.println("Passed.");
        } else {
            out.println("Failed!");
            System.exit(1);
        }
    }

    // (Dbg) Prints the buffer's current status info
    private static void bbStatus(ByteBuffer bb) {
        rem = bb.remaining();
        pos = bb.position();
        out.println("\nRemaining bytes: "+rem);
        out.println("position: "+pos);
        out.println("========================================");
    }

    // Shorthand function to wrap a byte array in a little-endian bytebuffer
    private static ByteBuffer mkLEBB(byte[] ba) {
        return ByteBuffer.wrap(ba).order(ByteOrder.LITTLE_ENDIAN);
    }

}
