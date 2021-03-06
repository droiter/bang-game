/*
 * Copyright (c) 2003-2006 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.jme.util.export.binary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import com.jme.util.export.ByteUtils;
import com.jme.util.export.OutputCapsule;
import com.jme.util.export.Savable;

public class BinaryOutputCapsule implements OutputCapsule {

    public static final int NULL_OBJECT = -1;
    public static final int DEFAULT_OBJECT = -2;

    public static byte[] NULL_BYTES = new byte[] { (byte) -1 };
    public static byte[] DEFAULT_BYTES = new byte[] { (byte) -2 };

    protected ByteArrayOutputStream baos;
    protected byte[] bytes;
    protected BinaryExporter exporter;
    protected BinaryClassObject cObj;

    public BinaryOutputCapsule(BinaryExporter exporter, BinaryClassObject bco) {
        this.baos = new ByteArrayOutputStream();
        this.exporter = exporter;
        this.cObj = bco;
    }

    @Override
    public void write(byte value, String name, byte defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.BYTE);
        write(value);
    }

    @Override
    public void write(byte[] value, String name, byte[] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.BYTE_1D);
        write(value);
    }

    @Override
    public void write(byte[][] value, String name, byte[][] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.BYTE_2D);
        write(value);
    }

    @Override
    public void write(int value, String name, int defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.INT);
        write(value);
    }

    @Override
    public void write(int[] value, String name, int[] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.INT_1D);
        write(value);
    }

    @Override
    public void write(int[][] value, String name, int[][] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.INT_2D);
        write(value);
    }

    @Override
    public void write(float value, String name, float defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.FLOAT);
        write(value);
    }

    @Override
    public void write(float[] value, String name, float[] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.FLOAT_1D);
        write(value);
    }

    @Override
    public void write(float[][] value, String name, float[][] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.FLOAT_2D);
        write(value);
    }

    @Override
    public void write(double value, String name, double defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.DOUBLE);
        write(value);
    }

    @Override
    public void write(double[] value, String name, double[] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.DOUBLE_1D);
        write(value);
    }

    @Override
    public void write(double[][] value, String name, double[][] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.DOUBLE_2D);
        write(value);
    }

    @Override
    public void write(long value, String name, long defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.LONG);
        write(value);
    }

    @Override
    public void write(long[] value, String name, long[] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.LONG_1D);
        write(value);
    }

    @Override
    public void write(long[][] value, String name, long[][] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.LONG_2D);
        write(value);
    }

    @Override
    public void write(short value, String name, short defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.SHORT);
        write(value);
    }

    @Override
    public void write(short[] value, String name, short[] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.SHORT_1D);
        write(value);
    }

    @Override
    public void write(short[][] value, String name, short[][] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.SHORT_2D);
        write(value);
    }

    @Override
    public void write(boolean value, String name, boolean defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.BOOLEAN);
        write(value);
    }

    @Override
    public void write(boolean[] value, String name, boolean[] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.BOOLEAN_1D);
        write(value);
    }

    @Override
    public void write(boolean[][] value, String name, boolean[][] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.BOOLEAN_2D);
        write(value);
    }

    @Override
    public void write(String value, String name, String defVal) throws IOException {
        if ( value == null ? defVal == null : value.equals( defVal ) ) return;
        writeAlias(name, BinaryClassField.STRING);
        write(value);
    }

    @Override
    public void write(String[] value, String name, String[] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.STRING_1D);
        write(value);
    }

    @Override
    public void write(String[][] value, String name, String[][] defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.STRING_2D);
        write(value);
    }

    @Override
    public void write(BitSet value, String name, BitSet defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.BITSET);
        write(value);
    }

    @Override
    public void write(Savable object, String name, Savable defVal) throws IOException {
        if (object == defVal) return;
        writeAlias(name, BinaryClassField.SAVABLE);
        write(object);
    }

    @Override
    public void write(Savable[] objects, String name, Savable[] defVal) throws IOException {
        if (objects == defVal) return;
        writeAlias(name, BinaryClassField.SAVABLE_1D);
        write(objects);
    }

    @Override
    public void write(Savable[][] objects, String name, Savable[][] defVal) throws IOException {
        if (objects == defVal) return;
        writeAlias(name, BinaryClassField.SAVABLE_2D);
        write(objects);
    }

    @Override
    public void write(FloatBuffer value, String name, FloatBuffer defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.FLOATBUFFER);
        write(value);
    }

    @Override
    public void write(IntBuffer value, String name, IntBuffer defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.INTBUFFER);
        write(value);
    }

    @Override
    public void write(ByteBuffer value, String name, ByteBuffer defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.BYTEBUFFER);
        write(value);
    }

    @Override
    public void write(ShortBuffer value, String name, ShortBuffer defVal) throws IOException {
        if (value == defVal) return;
        writeAlias(name, BinaryClassField.SHORTBUFFER);
        write(value);
    }

    @Override
    public void writeFloatBufferArrayList(List<FloatBuffer> array, String name, List<FloatBuffer> defVal) throws IOException {
        if (array == defVal) return;
        writeAlias(name, BinaryClassField.FLOATBUFFER_ARRAYLIST);
        writeFloatBufferArrayList(array);
    }

    @Override
    public void writeSavableArrayList(List<?> array, String name, List<?> defVal) throws IOException {
        if (array == defVal) return;
        writeAlias(name, BinaryClassField.SAVABLE_ARRAYLIST);
        writeSavableArrayList(array);
    }

    protected void writeAlias(String name, byte fieldType) throws IOException {
        if (cObj.nameFields.get(name) == null)
        generateAlias(name, fieldType);

        byte alias = cObj.nameFields.get(name).alias;
        write(alias);
    }

    // XXX: The generation of aliases is limited to 256 possible values.
    // If we run into classes with more than 256 fields, we need to expand this.
    // But I mean, come on...
    protected void generateAlias(String name, byte type) {
        byte alias = (byte) cObj.nameFields.size();
        cObj.nameFields.put(name, new BinaryClassField(name, alias, type));
    }

    @Override
    public boolean equals(Object arg0) {
        if (!(arg0 instanceof BinaryOutputCapsule))
        return false;

        byte[] other = ((BinaryOutputCapsule) arg0).bytes;
        if (bytes.length != other.length)
        return false;
        return Arrays.equals(bytes, other);
    }

    public void finish() {
        //renamed to finish as 'finalize' in java.lang.Object should not be overridden like this
        // - finalize should not be called directly but is called by garbage collection!!!
        bytes = baos.toByteArray();
        baos = null;
    }

    // byte primitive

    protected void write(byte value) throws IOException {
        baos.write(value);
    }

    protected void write(byte[] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        baos.write(value);
    }

    protected void write(byte[][] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    // int primitive

    protected void write(int value) throws IOException {
        baos.write(deflate(ByteUtils.convertToBytes(value)));
    }

    protected void write(int[] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    protected void write(int[][] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    // float primitive

    protected void write(float value) throws IOException {
        baos.write(ByteUtils.convertToBytes(value));
    }

    protected void write(float[] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    protected void write(float[][] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    // double primitive

    protected void write(double value) throws IOException {
        baos.write(ByteUtils.convertToBytes(value));
    }

    protected void write(double[] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    protected void write(double[][] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    // long primitive

    protected void write(long value) throws IOException {
        baos.write(deflate(ByteUtils.convertToBytes(value)));
    }

    protected void write(long[] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    protected void write(long[][] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    // short primitive

    protected void write(short value) throws IOException {
        baos.write(ByteUtils.convertToBytes(value));
    }

    protected void write(short[] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    protected void write(short[][] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    // boolean primitive

    protected void write(boolean value) throws IOException {
        baos.write(ByteUtils.convertToBytes(value));
    }

    protected void write(boolean[] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    protected void write(boolean[][] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    // String

    protected void write(String value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length());
        byte[] bytes = value.getBytes();
        baos.write(bytes);
    }

    protected void write(String[] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    protected void write(String[][] value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.length);
        for (int x = 0; x < value.length; x++)
        write(value[x]);
    }

    // BitSet

    protected void write(BitSet value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        write(value.size());
        //TODO: MAKE THIS SMALLER
        for (int x = 0, max = value.size(); x < max; x++)
        write(value.get(x));
    }

    // DEFLATOR for int and long

    protected static byte[] deflate(byte[] bytes) {
        int size = bytes.length;
        if (size == 4) {
            int possibleMagic = ByteUtils.convertIntFromBytes(bytes);
            if (possibleMagic == NULL_OBJECT)
            return NULL_BYTES;
            else if (possibleMagic == DEFAULT_OBJECT)
            return DEFAULT_BYTES;
        }
        for (int x = 0; x < bytes.length; x++) {
            if (bytes[x] != 0)
            break;
            size--;
        }
        if (size == 0)
        return new byte[1];

        byte[] rVal = new byte[1 + size];
        rVal[0] = (byte) size;
        for (int x = 1; x < rVal.length; x++)
        rVal[x] = bytes[bytes.length - size - 1 + x];

        return rVal;
    }

    // BinarySavable

    protected void write(Savable object)
    throws IOException {
        int id = exporter.processBinarySavable(object);
        write(id);
    }

    // BinarySavable array

    protected void write(Savable[] objects)
    throws IOException {
        if (objects == null) {
            write(NULL_OBJECT);
            return;
        }
        write(objects.length);
        for (int x = 0; x < objects.length; x++) {
            write(objects[x]);
        }
    }

    protected void write(Savable[][] objects)
    throws IOException {
        if (objects == null) {
            write(NULL_OBJECT);
            return;
        }
        write(objects.length);
        for (int x = 0; x < objects.length; x++) {
            write(objects[x]);
        }
    }

    // ArrayList<BinarySavable>

    protected void writeSavableArrayList(List<?> array) throws IOException {
        if (array == null) {
            write(NULL_OBJECT);
            return;
        }
        write(array.size());
        for (Object bs : array) {
            write((Savable) bs);
        }
    }

    protected void writeSavableArrayListArray(List<?>[] array) throws IOException {
        if (array == null) {
            write(NULL_OBJECT);
            return;
        }
        write(array.length);
        for (List<?> bs : array) {
            writeSavableArrayList(bs);
        }
    }

    protected void writeSavableArrayListArray2D(List<?>[][] array) throws IOException {
        if (array == null) {
            write(NULL_OBJECT);
            return;
        }
        write(array.length);
        for (List<?>[] bs : array) {
            writeSavableArrayListArray(bs);
        }
    }

    // ArrayList<FloatBuffer>

    protected void writeFloatBufferArrayList(List<FloatBuffer> array) throws IOException {
        if (array == null) {
            write(NULL_OBJECT);
            return;
        }
        write(array.size());
        for (FloatBuffer bs : array) {
            write(bs);
        }
    }

    // NIO BUFFERS
    // float buffer

    protected void write(FloatBuffer value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        value.clear();
        int length = value.capacity();
        write(length);
        for (int x = 0; x < length; x++) {
            write(value.get());
        }
        value.rewind();
    }

    // int buffer

    protected void write(IntBuffer value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        value.clear();
        int length = value.capacity();
        write(length);
        for (int x = 0; x < length; x++) {
            write(value.get());
        }
        value.rewind();
    }

    // byte buffer

    protected void write(ByteBuffer value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        value.clear();
        int length = value.capacity();
        write(length);
        for (int x = 0; x < length; x++) {
            write(value.get());
        }
        value.rewind();
    }

    // short buffer

    protected void write(ShortBuffer value) throws IOException {
        if (value == null) {
            write(NULL_OBJECT);
            return;
        }
        value.clear();
        int length = value.capacity();
        write(length);
        for (int x = 0; x < length; x++) {
            write(value.get());
        }
        value.rewind();
    }
}
