package com.github.tricksteronline;/*
Create.java: this file is part of the TNT program.

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
import com.github.tricksteronline.JBL;
import com.github.tricksteronline.NORI;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import static java.lang.System.out;
/**
Class Description:
For all intents and purposes, the Create class is meant to be the interface for
creating new NORI files from suitable bitmap images. It will rely heavily on
config files which will be specified by user input.

Dev Notes:
Creating new NORI files is honestly of little use to the Libre Trickster project
and this part of the program is little more than a curiosity.

Development Priority: LOW
*/
public class Create {
    // class variables
    public static int maxNoF=0,maxNoP=0,pos=0;
    public static int pdex0=0,pdex1=0,pdex2=0,pdex3=0,pdex4=0,pdex5=0,pdex6=0;
    public static byte[] nfba, palette, imgData, fba;
    public static int[] bmp_id, point_x, point_y, opacity, flip_axis;
    public static int[] blend_mode, flag_param;
    // constructor for Create class
    public Create(File config, String bmpDir) {
        try {
            out.println("\nGathering data from config file...");
            // Set the NORI file vars
            NORI.setNFileVars(config,1);
            NORI.checkDir();
            out.println("NORI filename: "+ NORI.name);
            // Get XML Data
            getConfigData(config);
            // Setup NORI file byte array and bytebuffer
            nfba = new byte[NORI.fsize];
            ByteBuffer nbb = mkLEBB(nfba);
            // Add NORI header to the nfba
            addNoriHdr(nbb);
            // Add GAWI header to the nfba
            addGawiHdr(nbb);
            // Add Palette section if it exists; it won't, future-proofing a bit
            if (NORI.hasPalette ==1) addPalSection(nbb);
            // Add BMP Offsets
            addBmpOffsets(nbb);
            // Get image data from BMP files
            imgData = getImgData(bmpDir);
            ByteBuffer dbb = mkLEBB(imgData);
            // Add BMP specs and data
            addBmpSection(nbb, dbb);
            // Add Animation Offsets
            addAnimOffsets(nbb);
            // Get XFB
            NORI.xfb = file2BA(NORI.dir + "xfb" + NORI.noriVer + ".bin");
            // Add Anims, Frames, Plane Data, & xfb
            addAnims(nbb);
            out.println("Finalizing file...");

            // Set BMP name and location, then write BMP to file
            File nori = new File(NORI.dir + NORI.name);
            Files.write(nori.toPath(),nfba);
            out.println("NORI File Creation Complete.\n");
        }
        catch (Exception ex)
        {
            out.println("Error in (CM):\n" + ex);
        }
    }

    private static void addNoriHdr(ByteBuffer bb) {
        bb.putInt(NORI.fsig);
        bb.putInt(NORI.noriVer);
        bb.putInt(NORI.nParam1);
        bb.putInt(NORI.nParam2);
        bb.putInt(NORI.nParam3);
        bb.putInt(NORI.nParam4);
        bb.putInt(NORI.nParam5);
        bb.putInt(NORI.anims);
        bb.putInt(NORI.woGawi);
        bb.putInt(NORI.fsize);
    }

    private static void addGawiHdr(ByteBuffer bb) {
        bb.putInt(NORI.gsig);
        bb.putInt(NORI.gawiVer);
        bb.putInt(NORI.bpp);
        bb.putInt(NORI.compressed);
        bb.putInt(NORI.hasPalette);
        bb.putInt(NORI.gParam4);
        bb.putInt(NORI.gParam5);
        bb.putInt(NORI.gParam6);
        bb.putInt(NORI.gParam7);
        bb.putInt(NORI.numBMP);
        bb.putInt(NORI.gsize);
    }

    private static void addPalSection(ByteBuffer bb) {
        bb.putInt(NORI.psig);
        bb.putInt(NORI.palVer);
        bb.putInt(NORI.pParam1);
        bb.putInt(NORI.pParam2);
        bb.putInt(NORI.pParam3);
        bb.putInt(NORI.pParam4);
        bb.putInt(NORI.divided);
        bb.putInt(NORI.psize);
        NORI.pb = file2BA(NORI.dir + NORI.name +"_pal.bin");
        bb.put(NORI.pb);
        if (NORI.psize ==808) {
            bb.putInt(NORI.mainS);
            bb.putInt(NORI.mainE);
        }
    }

    private static void addBmpOffsets(ByteBuffer bb) {
        for (int i = 0; i < NORI.numBMP; i++) {
            bb.putInt(NORI.bmpOffsets[i]);
        }
    }

    private static byte[] getImgData(String bmpDir) {
        // Setup byte array where pixel data will go
        int ids=0;
        for (int i = 0; i < NORI.numBMP; i++) {
            ids += NORI.bmpSpecs[i][1];
        }
        byte[] ba = new byte[ids];
        ByteBuffer bb = mkLEBB(ba);
        try {
            // Gather the list of bmp files
            File dataDir = new File(bmpDir);
            String[] tmpFL = dataDir.list();
            String[] fl = cleanFL(tmpFL);
            // Alphabetic ordering
            Arrays.sort(fl);
            // Pull the file contents into a byte array for later use
            out.println("Absorbing BMP files:");
            for (int i=0; i < fl.length; i++) {
                // output full file name
                out.println(bmpDir + fl[i]);
                // read bmp into a byte array, then wrap in a bytebuffer
                byte[] bmp = file2BA(bmpDir + fl[i]);
                ByteBuffer bbb = mkLEBB(bmp);
                // Strip the header off the image
                bbb.position(10);
                int pxStart = bbb.getInt();
                //out.println("pxStart: "+pxStart);
                int pxLen = bbb.capacity() - pxStart;
                //out.println("pxLength: "+pxLen);
                bbb.position(pxStart);
                byte[] hdrless = new byte[pxLen];
                bbb.get(hdrless,0,pxLen);
                // Set BMP header vars from xml data
                JBL.setBmpVars(NORI.bmpSpecs[i][2], NORI.bmpSpecs[i][3], NORI.bpp);
                // NORI format uses top-down scanlines
                byte[] revData = JBL.reverseRows(hdrless);
                // Strip any padding on the pixels
                byte[] rawData = JBL.stripPadding(revData);
                // Add raw data to ba byte array
                bb.put(rawData);
            }
        }
        catch (Exception ex) {
            out.println("Error in (getImgData):\n"+ex);
        }
        return ba;
    }

    private static void addBmpSection(ByteBuffer bb, ByteBuffer dbb) {
        String dcErr,manualFix;
        dcErr="Error: dcount not 1, space was added for BMP id: ";
        manualFix="To solve, manually fix: fsize, gsize, bmpOffsets, & dcount";
        for (int i = 0; i < NORI.numBMP; i++) {
            int addSpace;

            bb.putInt(NORI.bmpSpecs[i][0]);
            bb.putInt(NORI.bmpSpecs[i][1]);
            bb.putInt(NORI.bmpSpecs[i][2]);
            bb.putInt(NORI.bmpSpecs[i][3]);
            bb.putInt(NORI.bmpSpecs[i][4]);
            bb.putInt(NORI.bmpSpecs[i][5]);
            bb.putInt(NORI.bmpSpecs[i][6]);

            byte[] data = new byte[NORI.bmpSpecs[i][1]];
            dbb.get(data,0, NORI.bmpSpecs[i][1]);
            bb.put(data);
            pos = bb.position();
            if (NORI.bmpSpecs[i][0] != 1) {
                if (i != (NORI.numBMP -1)) {
                    addSpace = NORI.bmpOffsets[i + 1]- NORI.bmpOffsets[i] - data.length;
                } else {
                    addSpace = (NORI.gsize + 40) - pos;
                }
                out.println(dcErr + i + "\n" + manualFix);
                bb.position(pos + addSpace);
            }
        }
    }

    private static void addAnimOffsets(ByteBuffer bb) {
        for (int i = 0; i < NORI.anims; i++) {
            bb.putInt(NORI.animOffsets[i]);
        }
    }

    private static void addAnims(ByteBuffer bb) {
        for (int i = 0; i < NORI.anims; i++) {
            byte[] animName = (NORI.animName[i]).getBytes();
            pos = bb.position();
            bb.put(animName);
            bb.position(pos+32);
            bb.putInt(NORI.frames[i]);
            addFrameOffsets(bb,i);
            addFrameData(bb,i);
        }
    }

    private static void addFrameOffsets(ByteBuffer bb, int a) {
        for (int i = 0; i < NORI.frames[a]; i++) {
            bb.putInt(NORI.frameOffsets[a][i]);
        }
    }

    // Set actual data for frames (and planes)
    private static void addFrameData(ByteBuffer bb, int a) {
        for (int i = 0; i < NORI.frames[a]; i++) {
            bb.putInt(NORI.frameData[a][i][0]);
            bb.putInt(NORI.frameData[a][i][1]);
            addPlaneData(bb, a, i);
        }
    }

    private static void addPlaneData(ByteBuffer bb, int a, int f) {
        for (int i = 0; i < NORI.frameData[a][f][1]; i++) {
            bb.putInt(NORI.planeData[a][f][i][0]);
            bb.putInt(NORI.planeData[a][f][i][1]);
            bb.putInt(NORI.planeData[a][f][i][2]);
            bb.putInt(NORI.planeData[a][f][i][3]);
            bb.putInt(NORI.planeData[a][f][i][4]);
            bb.putInt(NORI.planeData[a][f][i][5]);
            bb.putInt(NORI.planeData[a][f][i][6]);
        }
        // Skip through xtraFrameBytes
        bb.put(NORI.xfb);
    }

    // Cleans the file list, if user is stupid, to make sure only bmp get in
    private static String[] cleanFL(String[] tmp) {
        String[] cfl = new String[NORI.numBMP];
        int x=0;

        for (String s : tmp) {
            if ((s.toLowerCase()).endsWith(".bmp")) {
                cfl[x++] = s;
            }
        }

        return cfl;
    }

    private static void getConfigData(File config) {
        try {
            // Make document object from config file
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbf.newDocumentBuilder();
            Document cfg = dBuilder.parse(config);
            cfg.getDocumentElement().normalize();
            // Set NORI Header and GAWI Header Elements
            Element noriHdr = getElementByTagName(cfg,"NORI_HDR");
            Element gawiHdr = getElementByTagName(cfg,"GAWI_HDR");
            // Get NORI Header Data
            NORI.fsig = getIntVal(noriHdr,"fsig");
            NORI.noriVer = getIntVal(noriHdr,"noriver");
            NORI.nParam1 = getIntVal(noriHdr,"nparam1");
            NORI.nParam2 = getIntVal(noriHdr,"nparam2");
            NORI.nParam3 = getIntVal(noriHdr,"nparam3");
            NORI.nParam4 = getIntVal(noriHdr,"nparam4");
            NORI.nParam5 = getIntVal(noriHdr,"nparam5");
            NORI.anims = getIntVal(noriHdr,"anims");
            NORI.woGawi = getIntVal(noriHdr,"woGawi");
            NORI.fsize = getIntVal(noriHdr,"fsize");
            // Get GAWI Header Data
            NORI.gsig = getIntVal(gawiHdr,"gsig");
            NORI.gawiVer = getIntVal(gawiHdr,"gawiver");
            NORI.bpp = getIntVal(gawiHdr,"bpp");
            NORI.compressed = getIntVal(gawiHdr,"compressed");
            NORI.hasPalette = getIntVal(gawiHdr,"hasPalette");
            NORI.gParam4 = getIntVal(gawiHdr,"gparam4");
            NORI.gParam5 = getIntVal(gawiHdr,"gparam5");
            NORI.gParam6 = getIntVal(gawiHdr,"gparam6");
            NORI.gParam7 = getIntVal(gawiHdr,"gparam7");
            NORI.numBMP = getIntVal(gawiHdr,"numBMP");
            NORI.gsize = getIntVal(gawiHdr,"gsize");
            // Get BMP Offsets
            NORI.bmpOffsets = getIntArrByTag(cfg,"bmpOffset");
            // Get BMP Specs
            NORI.bmpSpecs = new int[NORI.numBMP][7];
            int[] dcount  = getIntArrByTag(cfg,"dcount");
            int[] dlen    = getIntArrByTag(cfg,"dlen");
            int[] w       = getIntArrByTag(cfg,"w");
            int[] h       = getIntArrByTag(cfg,"h");
            int[] bparam4 = getIntArrByTag(cfg,"bparam4");
            int[] pos_x   = getIntArrByTag(cfg,"pos_x");
            int[] pos_y   = getIntArrByTag(cfg,"pos_y");
            for (int bmp = 0; bmp < NORI.numBMP; bmp++) {
                NORI.bmpSpecs[bmp][0] = dcount[bmp];
                NORI.bmpSpecs[bmp][1] = dlen[bmp];
                NORI.bmpSpecs[bmp][2] = w[bmp];
                NORI.bmpSpecs[bmp][3] = h[bmp];
                NORI.bmpSpecs[bmp][4] = bparam4[bmp];
                NORI.bmpSpecs[bmp][5] = pos_x[bmp];
                NORI.bmpSpecs[bmp][6] = pos_y[bmp];
            }
            // Get Animation Offsets
            NORI.animOffsets = getIntArrByTag(cfg,"animOffset");
            // Get Animation Data
            NORI.animName = getStrArrByTag(cfg,"name");
            NORI.frames = getIntArrByTag(cfg,"frames");
            // Prep Frame Data arrays
            maxNoF = getMax(NORI.frames);
            NORI.frameOffsets = new int[NORI.anims][maxNoF];
            NORI.frameData = new int[NORI.anims][maxNoF][2];
            // Get Frame Data Arrays
            int[] frameOff = getIntArrByTag(cfg,"frameOffset");
            int[] delays   = getIntArrByTag(cfg,"delay");
            int[] planes   = getIntArrByTag(cfg,"planes");
            // Prep Plane Data arrays
            maxNoP = getMax(planes);
            NORI.planeData = new int[NORI.anims][maxNoF][maxNoP][7];
            // Get Plane Data Arrays
            bmp_id     = getIntArrByTag(cfg,"bmp_id");
            point_x    = getIntArrByTag(cfg,"point_x");
            point_y    = getIntArrByTag(cfg,"point_y");
            opacity    = getIntArrByTag(cfg,"opacity");
            flip_axis  = getIntArrByTag(cfg,"flip_axis");
            blend_mode = getIntArrByTag(cfg,"blend_mode");
            flag_param = getIntArrByTag(cfg,"flag_param");
            // Get Frame Data
            int dex0=0,dex1=0,dex2=0;
            for (int a = 0; a < NORI.anims; a++) {
                for (int f = 0; f < NORI.frames[a]; f++) {
                    NORI.frameOffsets[a][f] = frameOff[dex0++];
                    NORI.frameData[a][f][0] = delays[dex1++];
                    NORI.frameData[a][f][1] = planes[dex2++];
                    // Get Plane Data
                    getPlaneData(cfg,a,f);
                }
            }
        } catch(Exception ex) {
            out.println("Error in (getConfigData):\n"+ex);
        }
    }

    private static void getPlaneData(Document cfg, int a, int f) {
        for (int p = 0; p < NORI.frameData[a][f][1]; p++) {
            NORI.planeData[a][f][p][0] = bmp_id[pdex0++];
            NORI.planeData[a][f][p][1] = point_x[pdex1++];
            NORI.planeData[a][f][p][2] = point_y[pdex2++];
            NORI.planeData[a][f][p][3] = opacity[pdex3++];
            NORI.planeData[a][f][p][4] = flip_axis[pdex4++];
            NORI.planeData[a][f][p][5] = blend_mode[pdex5++];
            NORI.planeData[a][f][p][6] = flag_param[pdex6++];
        }
    }

    // An anti-duplication + better readability function
    private static int getMax(int[] array) {
        int max=0;

        for (int x : array) {
            if (x > max) {
                max=x;
            }
        }

        return max;
    }

    // Get single int array (int[]) by tagName
    private static int[] getIntArrByTag(Document cfg, String tag) {
        NodeList nl = cfg.getElementsByTagName(tag);
        int max = nl.getLength();
        int[] tmp = new int[max];
        for (int i = 0; i < max; i++) {
            Node n = nl.item(i);
            tmp[i] = toInt((n.getTextContent()).trim());
        }
        return tmp;
    }

    // Get single string array (String[]) by tagName
    private static String[] getStrArrByTag(Document cfg, String tag) {
        NodeList nl = cfg.getElementsByTagName(tag);
        int max = nl.getLength();
        String[] tmp = new String[max];
        for (int i = 0; i < max; i++) {
            Node n = nl.item(i);
            tmp[i] = (n.getTextContent()).trim();
        }
        return tmp;
    }

    // An anti-duplication + better readability function
    private static Element getElementByTagName(Document cfg, String tagName) {
        Node node0 = cfg.getElementsByTagName(tagName).item(0);
        return (Element) node0;
    }

    // An anti-duplication + better readability function
    private static int getIntVal(Element parentE, String tagName) {
        Node node0 = parentE.getElementsByTagName(tagName).item(0);
        return toInt((node0.getTextContent()).trim());
    }

    // Shorthand function to wrap a byte array in a little-endian bytebuffer
    private static ByteBuffer mkLEBB(byte[] ba) {
        return ByteBuffer.wrap(ba).order(ByteOrder.LITTLE_ENDIAN);
    }

    // An anti-duplication + better readability function
    private static byte[] file2BA(String fStr) {
        try {
            fba = Files.readAllBytes((new File(fStr)).toPath());
        } catch(Exception ex) {
            out.println("Error in (file2BA):\n"+ex);
        }
        return fba;
    }

    // An anti-duplication + better readability function
    private static int toInt(String str) {
        int i;

        try {
            i = Integer.parseInt(str);
        } catch(NumberFormatException e) {
            i = 0;
        }

        return i;
    }
}
