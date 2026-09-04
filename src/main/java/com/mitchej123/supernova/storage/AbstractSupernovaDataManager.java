package com.mitchej123.supernova.storage;

import com.falsepattern.chunk.api.DataManager;
import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.config.SupernovaConfig;
import com.mitchej123.supernova.light.SWMRNibbleArray;
import com.mitchej123.supernova.light.SupernovaChunk;
import com.mitchej123.supernova.util.WorldUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.nio.ByteBuffer;

/**
 * Shared base for block/sky RGB data managers.
 * Handles NBT persistence and packet synchronization for RGB nibble arrays.
 * Supports scalar↔RGB conversion when the save format doesn't match the running mode.
 */
abstract class AbstractSupernovaDataManager implements DataManager.SubChunkDataManager, DataManager.PacketDataManager, DataManager.CubicPacketDataManager {

    // Flag byte + up to 3 * 2048 bytes per section, 16 sections
    private static final int MAX_PACKET_BYTES = 16 * (1 + 3 * SWMRNibbleArray.ARRAY_SIZE);
    private static final int MAX_PACKET_BYTES_CUBIC = 1 + 3 * SWMRNibbleArray.ARRAY_SIZE;

    // Flag bits indicating which channels are present
    private static final byte FLAG_R = 1;
    private static final byte FLAG_G = 2;
    private static final byte FLAG_B = 4;

    private final String nbtKeyR;
    private final String nbtKeyG;
    private final String nbtKeyB;
    private final String idStr;
    private final String uninstallMsg;

    protected AbstractSupernovaDataManager(String nbtKeyR, String nbtKeyG, String nbtKeyB, String id, String uninstallMessage) {
        this.nbtKeyR = nbtKeyR;
        this.nbtKeyG = nbtKeyG;
        this.nbtKeyB = nbtKeyB;
        this.idStr = id;
        this.uninstallMsg = uninstallMessage;
    }

    protected abstract SWMRNibbleArray[] getNibblesR(SupernovaChunk chunk);

    protected abstract SWMRNibbleArray[] getNibblesG(SupernovaChunk chunk);

    protected abstract SWMRNibbleArray[] getNibblesB(SupernovaChunk chunk);

    @Override
    public String domain() {
        return Supernova.MODID;
    }

    @Override
    public String id() {
        return idStr;
    }

    @Override
    public String version() {
        return "2";
    }

    @Override
    public String newInstallDescription() {
        return null;
    }

    @Override
    public String uninstallMessage() {
        return uninstallMsg;
    }

    @Override
    public String versionChangeMessage(String priorVersion) {
        return null;
    }

    @Override
    public void writeSubChunkToNBT(Chunk chunk, ExtendedBlockStorage subChunk, NBTTagCompound nbt) {
        final int sectionY = subChunk.getYLocation() >> 4;
        final SupernovaChunk ext = (SupernovaChunk) chunk;
        writeNibbleToNBT(nbt, nbtKeyR, getNibblesR(ext), sectionY);
        if (!SupernovaConfig.isScalarMode()) {
            writeNibbleToNBT(nbt, nbtKeyG, getNibblesG(ext), sectionY);
            writeNibbleToNBT(nbt, nbtKeyB, getNibblesB(ext), sectionY);
        }
    }

    @Override
    public void readSubChunkFromNBT(Chunk chunk, ExtendedBlockStorage subChunk, NBTTagCompound nbt) {
        if (nbt == null) return;
        final int sectionY = subChunk.getYLocation() >> 4;
        final SupernovaChunk ext = (SupernovaChunk) chunk;
        final boolean hasG = nbt.hasKey(nbtKeyG, 7);
        final boolean hasB = nbt.hasKey(nbtKeyB, 7);

        if (SupernovaConfig.isScalarMode()) {
            readNibbleFromNBT(nbt, nbtKeyR, getNibblesR(ext), sectionY);
            // Scalar mode: if RGB data was saved, collapse max(R,G,B) into R
            if (hasG || hasB) {
                collapseRGBToScalarNBT(nbt, getNibblesR(ext), sectionY, hasG, hasB);
            }
        } else if(!hasG && !hasB) {
            // RGB mode loading scalar data: discard saved data entirely.
            // Nibbles stay UNINIT -> onChunkLoad detects no saved data -> triggers full BFS re-light.
        } else {
            readNibbleFromNBT(nbt, nbtKeyR, getNibblesR(ext), sectionY);
            // RGB mode: read G/B normally, or clone R if saved as scalar (sky light)
            if (hasG) {
                readNibbleFromNBT(nbt, nbtKeyG, getNibblesG(ext), sectionY);
            } else {
                cloneNibbleChannel(getNibblesR(ext), getNibblesG(ext), sectionY);
            }
            if (hasB) {
                readNibbleFromNBT(nbt, nbtKeyB, getNibblesB(ext), sectionY);
            } else {
                cloneNibbleChannel(getNibblesR(ext), getNibblesB(ext), sectionY);
            }
        }
    }

    private void collapseRGBToScalarNBT(NBTTagCompound nbt, SWMRNibbleArray[] rNibbles, int sectionY, boolean hasG, boolean hasB) {
        final int idx = nibbleIndex(sectionY);
        if (rNibbles == null || idx < 0 || idx >= rNibbles.length) return;
        final SWMRNibbleArray rNib = rNibbles[idx];
        if (rNib == null) return;

        final byte[] rData = rNib.getUpdatingStorage();
        if (rData == null) return;
        final byte[] gData = hasG ? nbt.getByteArray(nbtKeyG) : null;
        final byte[] bData = hasB ? nbt.getByteArray(nbtKeyB) : null;

        collapseMax(rData, gData, bData);
    }

    @Override
    public int maxPacketSize() {
        return MAX_PACKET_BYTES;
    }

    @Override
    public void writeToBuffer(Chunk chunk, int subChunkMask, boolean forceUpdate, ByteBuffer buffer) {
        final SupernovaChunk ext = (SupernovaChunk) chunk;
        for (int sectionY = 0; sectionY < 16; ++sectionY) {
            if ((subChunkMask & (1 << sectionY)) == 0) continue;
            writeSectionToBuffer(buffer, getNibblesR(ext), getNibblesG(ext), getNibblesB(ext), sectionY);
        }
    }

    @Override
    public void readFromBuffer(Chunk chunk, int subChunkMask, boolean forceUpdate, ByteBuffer buffer) {
        final SupernovaChunk ext = (SupernovaChunk) chunk;
        for (int sectionY = 0; sectionY < 16; ++sectionY) {
            if ((subChunkMask & (1 << sectionY)) == 0) continue;
            readSectionFromBuffer(buffer, getNibblesR(ext), getNibblesG(ext), getNibblesB(ext), sectionY);
        }
    }

    @Override
    public int maxPacketSizeCubic() {
        return MAX_PACKET_BYTES_CUBIC;
    }

    @Override
    public void writeToBuffer(Chunk chunk, ExtendedBlockStorage blockStorage, ByteBuffer buffer) {
        final int sectionY = blockStorage.getYLocation() >> 4;
        final SupernovaChunk ext = (SupernovaChunk) chunk;
        writeSectionToBuffer(buffer, getNibblesR(ext), getNibblesG(ext), getNibblesB(ext), sectionY);
    }

    @Override
    public void readFromBuffer(Chunk chunk, ExtendedBlockStorage blockStorage, ByteBuffer buffer) {
        final int sectionY = blockStorage.getYLocation() >> 4;
        final SupernovaChunk ext = (SupernovaChunk) chunk;
        readSectionFromBuffer(buffer, getNibblesR(ext), getNibblesG(ext), getNibblesB(ext), sectionY);
    }

    static int nibbleIndex(int sectionY) {
        return sectionY - WorldUtil.getMinLightSection();
    }

    private static void writeNibbleToNBT(NBTTagCompound nbt, String key, SWMRNibbleArray[] nibbles, int sectionY) {
        if (nibbles == null) return;
        final int idx = nibbleIndex(sectionY);
        if (idx < 0 || idx >= nibbles.length) return;
        final SWMRNibbleArray nibble = nibbles[idx];
        if (nibble == null) return;
        final SWMRNibbleArray.SaveState state = nibble.getSaveState();
        if (state != null && state.data != null) {
            nbt.setByteArray(key, state.data);
        }
    }

    private static void readNibbleFromNBT(NBTTagCompound nbt, String key, SWMRNibbleArray[] nibbles, int sectionY) {
        if (nibbles == null) return;
        final int idx = nibbleIndex(sectionY);
        if (idx < 0 || idx >= nibbles.length) return;
        if (nbt.hasKey(key, 7)) { // 7 = TAG_Byte_Array
            nibbles[idx] = new SWMRNibbleArray(nbt.getByteArray(key).clone());
        }
    }

    private static void cloneNibbleChannel(SWMRNibbleArray[] src, SWMRNibbleArray[] dest, int sectionY) {
        if (src == null || dest == null) return;
        final int idx = nibbleIndex(sectionY);
        if (idx < 0 || idx >= src.length || idx >= dest.length) return;
        final SWMRNibbleArray srcNib = src[idx];
        if (srcNib == null) return;
        final byte[] srcData = srcNib.getUpdatingStorage();
        if (srcData == null) {
            // Source has no data -- check if it's full
            if (srcNib.isFullUpdating()) {
                dest[idx] = new SWMRNibbleArray(null, true);
                dest[idx].setFull();
            }
            return;
        }
        dest[idx] = new SWMRNibbleArray(srcData.clone());
    }

    static void writeSectionToBuffer(ByteBuffer buffer, SWMRNibbleArray[] rNibbles, SWMRNibbleArray[] gNibbles, SWMRNibbleArray[] bNibbles, int sectionY) {
        byte flags = 0;
        final byte[] rData = getNibbleData(rNibbles, sectionY);
        if (rData != null) flags |= FLAG_R;

        if (SupernovaConfig.isScalarMode()) {
            // Scalar mode: only write R channel
            buffer.put(flags);
            if (rData != null) buffer.put(rData);
        } else {
            final byte[] gData = getNibbleData(gNibbles, sectionY);
            final byte[] bData = getNibbleData(bNibbles, sectionY);
            if (gData != null) flags |= FLAG_G;
            if (bData != null) flags |= FLAG_B;

            buffer.put(flags);
            if (rData != null) buffer.put(rData);
            if (gData != null) buffer.put(gData);
            if (bData != null) buffer.put(bData);
        }
    }

    static void readSectionFromBuffer(ByteBuffer buffer, SWMRNibbleArray[] rNibbles, SWMRNibbleArray[] gNibbles, SWMRNibbleArray[] bNibbles, int sectionY) {
        final byte flags = buffer.get();
        final boolean hasR = (flags & FLAG_R) != 0;
        final boolean hasG = (flags & FLAG_G) != 0;
        final boolean hasB = (flags & FLAG_B) != 0;

        if (SupernovaConfig.isScalarMode()) {
            // Scalar client: read all channels from buffer, collapse to max(R,G,B) in R
            byte[] rData = null, gData = null, bData = null;
            if (hasR) rData = readRawFromBuffer(buffer);
            if (hasG) gData = readRawFromBuffer(buffer);
            if (hasB) bData = readRawFromBuffer(buffer);

            if (rData != null) {
                if (gData != null || bData != null) {
                    collapseMax(rData, gData, bData);
                }
                if (rNibbles != null) {
                    final int idx = nibbleIndex(sectionY);
                    if (idx >= 0 && idx < rNibbles.length) {
                        rNibbles[idx] = new SWMRNibbleArray(rData);
                    }
                }
            }
        } else {
            if (hasR) readNibbleFromBuffer(buffer, rNibbles, sectionY);
            if (hasG) {
                readNibbleFromBuffer(buffer, gNibbles, sectionY);
            }
            if (hasB) {
                readNibbleFromBuffer(buffer, bNibbles, sectionY);
            }
            // Server is scalar (only R sent) -- clone R to G/B for uniform white lighting
            if (hasR && !hasG && !hasB) {
                cloneNibbleChannel(rNibbles, gNibbles, sectionY);
                cloneNibbleChannel(rNibbles, bNibbles, sectionY);
            }
        }
    }

    private static byte[] readRawFromBuffer(ByteBuffer buffer) {
        final byte[] data = new byte[SWMRNibbleArray.ARRAY_SIZE];
        buffer.get(data);
        return data;
    }

    private static byte[] getNibbleData(SWMRNibbleArray[] nibbles, int sectionY) {
        if (nibbles == null) return null;
        final int idx = nibbleIndex(sectionY);
        if (idx < 0 || idx >= nibbles.length) return null;
        final SWMRNibbleArray nibble = nibbles[idx];
        if (nibble == null) return null;
        return nibble.getVisibleData();
    }

    private static void readNibbleFromBuffer(ByteBuffer buffer, SWMRNibbleArray[] nibbles, int sectionY) {
        final byte[] data = new byte[SWMRNibbleArray.ARRAY_SIZE];
        buffer.get(data);
        if (nibbles == null) return;
        final int idx = nibbleIndex(sectionY);
        if (idx < 0 || idx >= nibbles.length) return;
        nibbles[idx] = new SWMRNibbleArray(data);
    }

    static void collapseMax(byte[] rData, byte[] gData, byte[] bData) {
        for (int i = 0; i < rData.length; ++i) {
            final int r = rData[i] & 0xFF;
            final int g = gData != null ? gData[i] & 0xFF : 0;
            final int b = bData != null ? bData[i] & 0xFF : 0;
            final int rLo = r & 0xF, rHi = (r >>> 4) & 0xF;
            final int gLo = g & 0xF, gHi = (g >>> 4) & 0xF;
            final int bLo = b & 0xF, bHi = (b >>> 4) & 0xF;
            rData[i] = (byte) ((Math.max(rHi, Math.max(gHi, bHi)) << 4) | Math.max(rLo, Math.max(gLo, bLo)));
        }
    }
}
