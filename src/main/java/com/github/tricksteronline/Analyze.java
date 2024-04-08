package com.github.tricksteronline;/*
Analyze.java: this file is part of the TNT program.

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
import com.github.tricksteronline.Analyzer;
import com.github.tricksteronline.NORI;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import static java.lang.System.out;
/**
Class Description:
This class provides a standalone interface for the Analyzer functionality.
More importantly, it can produce config files for pre-existing NORI files.

Dev Notes:
The xml config code here isn't entirely user friendly but that's not really my
fault. It's just the way the standard libs for xml are. Plus, the program is
handling a huge amount of data in single file so I think it is decent.

Development Priority: HIGH
*/
public class Analyze {
    // class variables

    // constructor for Analyze class
    public Analyze(byte[] ba, boolean createConfig) {
        try {
            // Wraps byte array in litte-endian bytebuffer
            ByteBuffer bb = ByteBuffer.wrap(ba).order(ByteOrder.LITTLE_ENDIAN);
            // Analyze the file
            Analyzer a = new Analyzer(bb);

            // make NORI config file
            if (createConfig) {
                writeCfg();
                File xfbFile = new File(NORI.dir +"xfb"+ NORI.noriVer +".bin");
                Files.write(xfbFile.toPath(), NORI.xfb);
                if (NORI.hasPalette ==1) {
                    File palFile = new File(NORI.dir + NORI.name +"_pal.bin");
                    Files.write(palFile.toPath(), NORI.pb);
                }
            }
        }
        catch(Exception ex) {
            out.println("Error in (OptA):\n"+ex);
        }
    }

    // Prepare and write NORI config file
    private static void writeCfg() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document cfg = docBuilder.newDocument();
            // Root Element
            Element root = cfg.createElement("NORI");
            root.setAttribute("name", NORI.name);
            cfg.appendChild(root);
            // NORI Header Elements
            Element noriHdr = cfg.createElement("NORI_HDR");
            root.appendChild(noriHdr);
            // NORI Header SubElements
            setNoriHdrVars(cfg, noriHdr);
            // GAWI Elements
            Element gawi = cfg.createElement("GAWI");
            root.appendChild(gawi);
            // GAWI Header Elements
            Element gawiHdr = cfg.createElement("GAWI_HDR");
            gawi.appendChild(gawiHdr);
            // NORI Header SubElements
            setGawiHdrVars(cfg, gawiHdr);
            // Palette Elements
            if (NORI.hasPalette == 1) {
                Element pal = cfg.createElement("PAL");
                gawi.appendChild(pal);
                setPaletteVars(cfg, pal);
            }
            // BMP Offset Elements
            for (int i = 0; i < NORI.numBMP; i++) {
                Element bmpOff = cfg.createElement("bmpOffset");
                bmpOff.setAttribute("id","" + i);
                bmpOff.appendChild(cfg.createTextNode("" + NORI.bmpOffsets[i]));
                gawi.appendChild(bmpOff);
            }
            // BMP Data Elements
            for (int i = 0; i < NORI.numBMP; i++) {
                Element bmp = cfg.createElement("BMP");
                bmp.setAttribute("id","" + i);
                bmp.setAttribute("offset", NORI.bmpOffsets[i] + "+" + NORI.bpos);
                gawi.appendChild(bmp);
                // BMP SubElements
                setBmpSpecs(cfg, bmp, i);
                Element bmpData = cfg.createElement("RGB"+ NORI.bpp +"DATA");
                bmp.appendChild(bmpData);
            }
            // Animation Offset Elements
            for (int i = 0; i < NORI.anims; i++) {
                Element animOff = cfg.createElement("animOffset");
                animOff.setAttribute("id",""+i);
                animOff.appendChild(cfg.createTextNode(""+ NORI.animOffsets[i]));
                root.appendChild(animOff);
            }
            // Animation Data Elements
            for (int i = 0; i < NORI.anims; i++) {
                Element anim = cfg.createElement("ANIM");
                anim.setAttribute("id","" + i);
                anim.setAttribute("offset", NORI.animOffsets[i] + "+" + NORI.apos);
                root.appendChild(anim);
                // Anim SubElements
                Element name = cfg.createElement("name");
                name.appendChild(cfg.createTextNode(NORI.animName[i]));
                anim.appendChild(name);
                mkSubElement(cfg, anim,"frames", NORI.frames[i]);
                setFrameOffsets(cfg, anim, i);
                // Frame Data and SubElements
                setFrames(cfg, anim, i);
            }

            // Prep xml data
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            String indentAmount = "{http://xml.apache.org/xslt}indent-amount";
            t.setOutputProperty(indentAmount,"2");
            // Output xml config file
            DOMSource src = new DOMSource(cfg);
            File config = new File(NORI.dir + NORI.name +".cfg");
            StreamResult file = new StreamResult(config);
            t.transform(src, file);
        }
        catch (Exception ex) {
            out.println("Error in (mkCfg):\n"+ex);
        }
    }

    private static void setNoriHdrVars(Document cfg, Element e) {
        mkSubElement(cfg, e, "fsig", NORI.fsig);
        mkSubElement(cfg, e, "noriver", NORI.noriVer);
        mkSubElement(cfg, e, "nparam1", NORI.nParam1);
        mkSubElement(cfg, e, "nparam2", NORI.nParam2);
        mkSubElement(cfg, e, "nparam3", NORI.nParam3);
        mkSubElement(cfg, e, "nparam4", NORI.nParam4);
        mkSubElement(cfg, e, "nparam5", NORI.nParam5);
        mkSubElement(cfg, e, "anims", NORI.anims);
        mkSubElement(cfg, e, "woGawi", NORI.woGawi);
        mkSubElement(cfg, e, "fsize", NORI.fsize);
    }

    private static void setGawiHdrVars(Document cfg, Element e) {
        mkSubElement(cfg, e, "gsig", NORI.gsig);
        mkSubElement(cfg, e, "gawiver", NORI.gawiVer);
        mkSubElement(cfg, e, "bpp", NORI.bpp);
        mkSubElement(cfg, e, "compressed", NORI.compressed);
        mkSubElement(cfg, e, "hasPalette", NORI.hasPalette);
        mkSubElement(cfg, e, "gparam4", NORI.gParam4);
        mkSubElement(cfg, e, "gparam5", NORI.gParam5);
        mkSubElement(cfg, e, "gparam6", NORI.gParam6);
        mkSubElement(cfg, e, "gparam7", NORI.gParam7);
        mkSubElement(cfg, e, "numBMP", NORI.numBMP);
        mkSubElement(cfg, e, "gsize", NORI.gsize);
    }

    private static void setPaletteVars(Document cfg, Element e) {
        mkSubElement(cfg, e, "psig", NORI.psig);
        mkSubElement(cfg, e, "palver", NORI.palVer);
        mkSubElement(cfg, e, "pparam1", NORI.pParam1);
        mkSubElement(cfg, e, "pparam2", NORI.pParam2);
        mkSubElement(cfg, e, "pparam3", NORI.pParam3);
        mkSubElement(cfg, e, "pparam4", NORI.pParam4);
        mkSubElement(cfg, e, "divided", NORI.divided);
        mkSubElement(cfg, e, "psize", NORI.psize);
        Element palData = cfg.createElement("RGB24DATA");
        e.appendChild(palData);
        if (NORI.psize == 808) {
            mkSubElement(cfg, e,"mainS", NORI.mainS);
            mkSubElement(cfg, e,"mainE", NORI.mainE);
        }
    }

    private static void setBmpSpecs(Document cfg, Element e, int i) {
        mkSubElement(cfg, e, "dcount", NORI.bmpSpecs[i][0]);
        mkSubElement(cfg, e, "dlen", NORI.bmpSpecs[i][1]);
        mkSubElement(cfg, e, "w", NORI.bmpSpecs[i][2]);
        mkSubElement(cfg, e, "h", NORI.bmpSpecs[i][3]);
        mkSubElement(cfg, e, "bparam4", NORI.bmpSpecs[i][4]);
        mkSubElement(cfg, e, "pos_x", NORI.bmpSpecs[i][5]);
        mkSubElement(cfg, e, "pos_y", NORI.bmpSpecs[i][6]);
    }

    private static void setFrameOffsets(Document cfg, Element e, int a) {
        // Frame Offset Elements
        for (int i = 0; i < NORI.frames[a]; i++) {
            Element frameOff = cfg.createElement("frameOffset");
            frameOff.setAttribute("id",""+i);
            frameOff.appendChild(cfg.createTextNode(""+ NORI.frameOffsets[a][i]));
            e.appendChild(frameOff);
        }
    }

    private static void setFrames(Document cfg, Element e, int a) {
        // Frame Offset Elements
        for (int i = 0; i < NORI.frames[a]; i++) {
            Element frame = cfg.createElement("frame");
            frame.setAttribute("id","" + i);
            frame.setAttribute("offset","" + NORI.frameOffsets[a][i]);
            e.appendChild(frame);
            mkSubElement(cfg,frame,"delay", NORI.frameData[a][i][0]);
            mkSubElement(cfg,frame,"planes", NORI.frameData[a][i][1]);
            setPlanes(cfg, frame, a, i);
            mkSubElement(cfg, frame,"xfb", NORI.xtraFrameBytes);
        }
    }

    private static void setPlanes(Document cfg, Element e, int a, int f) {
        for (int i = 0; i < NORI.frameData[a][f][1]; i++) {
            Element plane = cfg.createElement("plane");
            plane.setAttribute("id",""+i);
            e.appendChild(plane);
            mkSubElement(cfg,plane,"bmp_id", NORI.planeData[a][f][i][0]);
            mkSubElement(cfg,plane,"point_x", NORI.planeData[a][f][i][1]);
            mkSubElement(cfg,plane,"point_y", NORI.planeData[a][f][i][2]);
            mkSubElement(cfg,plane,"opacity", NORI.planeData[a][f][i][3]);
            mkSubElement(cfg,plane,"flip_axis", NORI.planeData[a][f][i][4]);
            mkSubElement(cfg,plane,"blend_mode", NORI.planeData[a][f][i][5]);
            mkSubElement(cfg,plane,"flag_param", NORI.planeData[a][f][i][6]);
        }
    }

    // Make Element child (Element's Element)
    private static void mkSubElement(Document cfg, Element e, String name, int val) {
        Element subE = cfg.createElement(name);
        subE.appendChild(cfg.createTextNode(""+val));
        e.appendChild(subE);
    }
}
