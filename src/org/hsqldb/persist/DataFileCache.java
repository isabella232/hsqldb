/* Copyright (c) 2001-2011, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb.persist;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.hsqldb.Database;
import org.hsqldb.HsqlException;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.FileAccess;
import org.hsqldb.lib.FileArchiver;
import org.hsqldb.lib.FileUtil;
import org.hsqldb.lib.Iterator;
import org.hsqldb.rowio.RowInputBinary180;
import org.hsqldb.rowio.RowInputBinaryDecode;
import org.hsqldb.rowio.RowInputInterface;
import org.hsqldb.rowio.RowOutputBinary180;
import org.hsqldb.rowio.RowOutputBinaryEncode;
import org.hsqldb.rowio.RowOutputInterface;
import org.hsqldb.store.BitMap;

/**
 * Acts as a manager for CACHED table persistence.<p>
 *
 * This contains the top level functionality. Provides file management services
 * and access.<p>
 *
 * Rewritten for 1.8.0 and 2.x
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.3.0
 * @since 1.7.2
 */
public class DataFileCache {

    protected FileAccess fa;

    // flags
    public static final int FLAG_ISSHADOWED = 1;
    public static final int FLAG_ISSAVED    = 2;
    public static final int FLAG_ROWINFO    = 3;
    public static final int FLAG_190        = 4;
    public static final int FLAG_HX         = 5;

    // file format fields
    static final int LONG_EMPTY_SIZE      = 4;        // empty space size
    static final int LONG_FREE_POS_POS    = 12;       // where iFreePos is saved
    static final int INT_SPACE_LIST_POS   = 24;       // empty space index
    static final int FLAGS_POS            = 28;
    static final int MIN_INITIAL_FREE_POS = 32;

    //
    public DataSpaceManager  spaceManager;
    static final int         initIOBufferSize = 4096;
    private static final int diskBlockSize    = 4096;

    //
    protected String   dataFileName;
    protected String   backupFileName;
    protected Database database;
    protected boolean  logEvents = true;

    // this flag is used externally to determine if a backup is required
    protected boolean fileModified;
    protected boolean cacheModified;
    protected int     dataFileScale;

    // post opening constant fields
    protected boolean cacheReadonly;

    //
    protected int cachedRowPadding;

    //
    protected int     initialFreePos;
    protected long    lostSpaceSize;
    protected long    spaceManagerPosition;
    protected long    fileStartFreePosition;
    protected boolean hasRowInfo = false;
    protected int     storeCount;

    // reusable input / output streams
    protected RowInputInterface rowIn;
    public RowOutputInterface   rowOut;

    //
    public long maxDataFileSize;

    //
    boolean is180;

    //
    protected RandomAccessInterface dataFile;
    protected volatile long         fileFreePosition;
    protected int                   maxCacheRows;     // number of Rows
    protected long                  maxCacheBytes;    // number of bytes
    protected Cache                 cache;

    //
    private RAShadowFile shadowFile;

    //
    ReadWriteLock lock      = new ReentrantReadWriteLock();
    Lock          readLock  = lock.readLock();
    Lock          writeLock = lock.writeLock();

    public DataFileCache(Database db, String baseFileName) {

        initParams(db, baseFileName);

        cache = new Cache(this);
    }

    /**
     * initial external parameters are set here.
     */
    protected void initParams(Database database, String baseFileName) {

        this.dataFileName   = baseFileName + Logger.dataFileExtension;
        this.backupFileName = baseFileName + Logger.backupFileExtension;
        this.database       = database;
        fa                  = database.logger.getFileAccess();
        dataFileScale       = database.logger.getDataFileScale();
        cachedRowPadding    = 8;

        if (dataFileScale > 8) {
            cachedRowPadding = dataFileScale;
        }

        initialFreePos = MIN_INITIAL_FREE_POS;

        if (initialFreePos < dataFileScale) {
            initialFreePos = dataFileScale;
        }

        cacheReadonly = database.isFilesReadOnly();
        maxCacheRows  = database.logger.propCacheMaxRows;
        maxCacheBytes = database.logger.propCacheMaxSize;
        maxDataFileSize = (long) Integer.MAX_VALUE * dataFileScale
                          * database.logger.getDataFileFactor();
        dataFile   = null;
        shadowFile = null;
    }

    /**
     * Opens the *.data file for this cache, setting the variables that
     * allow access to the particular database version of the *.data file.
     */
    public void open(boolean readonly) {

        if (database.logger.isStoredFileAccess()) {
            openStoredFileAccess(readonly);

            return;
        }

        fileFreePosition = initialFreePos;

        logInfoEvent("dataFileCache open start");

        try {
            boolean isNio = database.logger.propNioDataFile;
            int     fileType;

            if (database.isFilesInJar()) {
                fileType = ScaledRAFile.DATA_FILE_JAR;
            } else if (isNio) {
                fileType = ScaledRAFile.DATA_FILE_NIO;
            } else {
                fileType = ScaledRAFile.DATA_FILE_RAF;
            }

            if (readonly || database.isFilesInJar()) {
                dataFile = ScaledRAFile.newScaledRAFile(database,
                        dataFileName, readonly, fileType);

                dataFile.seek(FLAGS_POS);

                int flags = dataFile.readInt();

                is180 = !BitMap.isSet(flags, FLAG_190);

                if (BitMap.isSet(flags, FLAG_HX)) {
                    throw Error.error(ErrorCode.WRONG_DATABASE_FILE_VERSION);
                }

                dataFile.seek(LONG_FREE_POS_POS);

                fileFreePosition = dataFile.readLong();

                dataFile.seek(INT_SPACE_LIST_POS);

                spaceManagerPosition = dataFile.readInt()
                                       * DataSpaceManager.fixedBlockSizeUnit;

                initBuffers();

                return;
            }

            boolean preexists     = fa.isStreamElement(dataFileName);
            boolean isIncremental = database.logger.propIncrementBackup;
            boolean isSaved       = false;

            if (preexists) {
                dataFile = new ScaledRAFileSimple(database, dataFileName, "r");

                long    length       = dataFile.length();
                boolean wrongVersion = false;

                if (length > initialFreePos) {
                    dataFile.seek(FLAGS_POS);

                    int flags = dataFile.readInt();

                    isSaved       = BitMap.isSet(flags, FLAG_ISSAVED);
                    isIncremental = BitMap.isSet(flags, FLAG_ISSHADOWED);
                    is180         = !BitMap.isSet(flags, FLAG_190);

                    if (BitMap.isSet(flags, FLAG_HX)) {
                        wrongVersion = true;
                    }
                }

                dataFile.close();

                if (length > maxDataFileSize) {
                    throw Error.error(ErrorCode.WRONG_DATABASE_FILE_VERSION,
                                      "requires large database support");
                }

                if (wrongVersion) {
                    throw Error.error(ErrorCode.WRONG_DATABASE_FILE_VERSION);
                }

                if (isSaved && isIncremental) {
                    boolean existsBackup = fa.isStreamElement(backupFileName);

                    if (existsBackup) {
                        isSaved = false;
                    }
                }
            }

            if (isSaved) {
                if (isIncremental) {
                    deleteBackup();
                } else {
                    boolean existsBackup = fa.isStreamElement(backupFileName);

                    if (!existsBackup) {
                        backupFile(false);
                    }
                }
            } else {
                if (isIncremental) {
                    preexists = restoreBackupIncremental();
                } else {
                    preexists = restoreBackup();
                }
            }

            dataFile = ScaledRAFile.newScaledRAFile(database, dataFileName,
                    readonly, fileType);

            if (preexists) {
                dataFile.seek(FLAGS_POS);

                int flags = dataFile.readInt();

                is180 = !BitMap.isSet(flags, FLAG_190);

                dataFile.seek(LONG_EMPTY_SIZE);

                lostSpaceSize = dataFile.readLong();

                dataFile.seek(LONG_FREE_POS_POS);

                fileFreePosition      = dataFile.readLong();
                fileStartFreePosition = fileFreePosition;

                dataFile.seek(INT_SPACE_LIST_POS);

                spaceManagerPosition = dataFile.readInt()
                                       * DataSpaceManager.fixedBlockSizeUnit;

                openShadowFile();
            } else {
                initNewFile();
            }

            initBuffers();

            fileModified  = false;
            cacheModified = false;

            if (spaceManagerPosition != 0 || database.logger.propFileSpaces) {
                spaceManager = new DataSpaceManagerBlocks(this);
            } else {
                spaceManager = new DataSpaceManagerSimple(this);
            }

            logInfoEvent("dataFileCache open end");
        } catch (Throwable t) {
            logSevereEvent("dataFileCache open failed", t);
            close(false);

            throw Error.error(t, ErrorCode.FILE_IO_ERROR,
                              ErrorCode.M_DataFileCache_open, new Object[] {
                t.toString(), dataFileName
            });
        }
    }

    void openStoredFileAccess(boolean readonly) {

        fileFreePosition = initialFreePos;

        logInfoEvent("dataFileCache open start");

        try {
            int fileType = ScaledRAFile.DATA_FILE_STORED;

            if (readonly) {
                dataFile = ScaledRAFile.newScaledRAFile(database,
                        dataFileName, readonly, fileType);

                dataFile.seek(FLAGS_POS);

                int flags = dataFile.readInt();

                is180 = !BitMap.isSet(flags, FLAG_190);

                dataFile.seek(LONG_FREE_POS_POS);

                fileFreePosition = dataFile.readLong();

                initBuffers();

                return;
            }

            long    freesize      = 0;
            boolean preexists     = fa.isStreamElement(dataFileName);
            boolean isIncremental = database.logger.propIncrementBackup;
            boolean restore = database.getProperties().getDBModified()
                              == HsqlDatabaseProperties.FILES_MODIFIED;

            if (preexists && restore) {
                if (isIncremental) {
                    preexists = restoreBackupIncremental();
                } else {
                    preexists = restoreBackup();
                }
            }

            dataFile = ScaledRAFile.newScaledRAFile(database, dataFileName,
                    readonly, fileType);

            if (preexists) {
                dataFile.seek(LONG_EMPTY_SIZE);

                freesize = dataFile.readLong();

                dataFile.seek(LONG_FREE_POS_POS);

                fileFreePosition      = dataFile.readLong();
                fileStartFreePosition = fileFreePosition;

                dataFile.seek(FLAGS_POS);

                int flags = dataFile.readInt();

                is180 = !BitMap.isSet(flags, FLAG_190);

                openShadowFile();
            } else {
                initNewFile();
            }

            initBuffers();

            fileModified  = false;
            cacheModified = false;
            spaceManager  = new DataSpaceManagerSimple(this);

            logInfoEvent("dataFileCache open end");
        } catch (Throwable t) {
            logSevereEvent("dataFileCache open failed", t);
            close(false);

            throw Error.error(t, ErrorCode.FILE_IO_ERROR,
                              ErrorCode.M_DataFileCache_open, new Object[] {
                t.toString(), dataFileName
            });
        }
    }

    public DataFileCache(Database db, String sourceFileName, boolean defrag) {

        this.database    = db;
        dataFileName     = sourceFileName + Logger.newFileExtension;
        dataFileScale    = database.logger.getDataFileScale();
        cachedRowPadding = 8;

        if (dataFileScale > 8) {
            cachedRowPadding = dataFileScale;
        }

        initialFreePos = MIN_INITIAL_FREE_POS;

        if (initialFreePos < dataFileScale) {
            initialFreePos = dataFileScale;
        }

        maxCacheRows  = 1024;
        maxCacheBytes = 1024 * 4096;
        maxDataFileSize = (long) Integer.MAX_VALUE * dataFileScale
                          * database.logger.getDataFileFactor();

        try {
            if (database.logger.isStoredFileAccess()) {
                dataFile = ScaledRAFile.newScaledRAFile(database,
                        dataFileName, false, ScaledRAFile.DATA_FILE_STORED);
            } else {
                dataFile = new ScaledRAFileSimple(database, dataFileName,
                                                  "rw");
            }
        } catch (IOException e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, e);
        }

        //
        cache = new Cache(this);

        initNewFile();
        initBuffers();

        if (database.logger.propFileSpaces) {
            spaceManager = new DataSpaceManagerBlocks(this);
        } else {
            spaceManager = new DataSpaceManagerSimple(this);
        }
    }

    void initNewFile() {

        try {
            fileFreePosition      = initialFreePos;
            fileStartFreePosition = initialFreePos;

            dataFile.seek(LONG_FREE_POS_POS);
            dataFile.writeLong(fileFreePosition);

            // set shadowed flag;
            int flags = 0;

            if (database.logger.propIncrementBackup) {
                flags = BitMap.set(flags, FLAG_ISSHADOWED);
            }

            flags = BitMap.set(flags, FLAG_ISSAVED);
            flags = BitMap.set(flags, FLAG_190);

            dataFile.seek(FLAGS_POS);
            dataFile.writeInt(flags);
            dataFile.synch();

            is180 = false;
        } catch (IOException e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, e);
        }
    }

    void openShadowFile() {

        if (database.logger.propIncrementBackup
                && fileFreePosition != initialFreePos) {
            shadowFile = new RAShadowFile(database, dataFile, backupFileName,
                                          fileFreePosition, 1 << 14);
        }
    }

    void setIncrementBackup(boolean value) {

        writeLock.lock();

        try {
            dataFile.seek(FLAGS_POS);

            int flags = dataFile.readInt();

            if (value) {
                flags = BitMap.set(flags, FLAG_ISSHADOWED);
            } else {
                flags = BitMap.unset(flags, FLAG_ISSHADOWED);
            }

            dataFile.seek(FLAGS_POS);
            dataFile.writeInt(flags);
            dataFile.synch();

            fileModified = true;
        } catch (Throwable t) {
            logSevereEvent("backupFile failed", t);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Restores a compressed backup or the .data file.
     */
    private boolean restoreBackup() {

        // in case data file cannot be deleted, reset it
        deleteFile();

        try {
            FileAccess fa = database.logger.getFileAccess();

            if (fa.isStreamElement(backupFileName)) {
                FileArchiver.unarchive(backupFileName, dataFileName, fa,
                                       FileArchiver.COMPRESSION_ZIP);

                return true;
            }

            return false;
        } catch (Throwable t) {
            throw Error.error(t, ErrorCode.FILE_IO_ERROR,
                              ErrorCode.M_Message_Pair, new Object[] {
                t.toString(), backupFileName
            });
        }
    }

    /**
     * Restores in from an incremental backup
     */
    private boolean restoreBackupIncremental() {

        try {
            if (fa.isStreamElement(backupFileName)) {
                RAShadowFile.restoreFile(database, backupFileName,
                                         dataFileName);
                deleteBackup();

                return true;
            }

            return false;
        } catch (IOException e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, e);
        }
    }

    /**
     *  Parameter write indicates either an orderly close, or a fast close
     *  without backup.
     *
     *  When false, just closes the file.
     *
     *  When true, writes out all cached rows that have been modified and the
     *  free position pointer for the *.data file and then closes the file.
     */
    public void close(boolean write) {

        writeLock.lock();

        try {
            if (dataFile == null) {
                return;
            }

            if (spaceManager != null) {
                spaceManager.close();
            }

            if (write) {
                commitChanges();
            } else {
                if (shadowFile != null) {
                    shadowFile.close();

                    shadowFile = null;
                }
            }

            dataFile.close();
            logDetailEvent("dataFileCache file close");

            dataFile = null;

            if (!write) {
                return;
            }

            boolean empty = fileFreePosition == initialFreePos;

            if (empty) {
                deleteFile();
                deleteBackup();
            }
        } catch (HsqlException e) {
            throw e;
        } catch (Throwable t) {
            logSevereEvent("dataFileCache close failed", t);

            throw Error.error(t, ErrorCode.FILE_IO_ERROR,
                              ErrorCode.M_DataFileCache_close, new Object[] {
                t.toString(), dataFileName
            });
        } finally {
            writeLock.unlock();
        }
    }

    protected void clear() {

        writeLock.lock();

        try {
            cache.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public void adjustStoreCount(int adjust) {

        writeLock.lock();

        try {
            storeCount += adjust;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Commits all the changes to the file
     */
    public void commitChanges() {

        writeLock.lock();

        try {
            if (cacheReadonly) {
                return;
            }

            logInfoEvent("dataFileCache commit start");
            cache.saveAll();

            if (fileModified || spaceManager.isModified()) {

                // set empty
                dataFile.seek(LONG_EMPTY_SIZE);
                dataFile.writeLong(spaceManager.getLostBlocksSize());

                // set end
                dataFile.seek(LONG_FREE_POS_POS);
                dataFile.writeLong(fileFreePosition);
                dataFile.seek(INT_SPACE_LIST_POS);

                int pos = (int) (spaceManagerPosition
                                 / DataSpaceManager.fixedBlockSizeUnit);

                dataFile.writeInt(pos);

                // set saved flag;
                dataFile.seek(FLAGS_POS);

                int flags = dataFile.readInt();

                flags = BitMap.set(flags, FLAG_ISSAVED);

                dataFile.seek(FLAGS_POS);
                dataFile.writeInt(flags);
            }

            dataFile.synch();
            cache.logSynchEvent();

            fileModified          = false;
            cacheModified         = false;
            fileStartFreePosition = fileFreePosition;

            if (shadowFile != null) {
                shadowFile.close();

                shadowFile = null;
            }

            logDetailEvent("dataFileCache commit end");
        } catch (Throwable t) {
            logSevereEvent("dataFileCache commit failed", t);

            throw Error.error(t, ErrorCode.FILE_IO_ERROR,
                              ErrorCode.M_DataFileCache_close, new Object[] {
                t.toString(), dataFileName
            });
        } finally {
            writeLock.unlock();
        }
    }

    protected void initBuffers() {

        if (rowOut == null) {
            if (is180) {
                rowOut = new RowOutputBinary180(initIOBufferSize,
                                                cachedRowPadding);
            } else {
                rowOut = new RowOutputBinaryEncode(database.logger.getCrypto(),
                                                   initIOBufferSize,
                                                   cachedRowPadding);
            }
        }

        if (rowIn == null) {
            if (is180) {
                rowIn = new RowInputBinary180(new byte[initIOBufferSize]);
            } else {
                rowIn = new RowInputBinaryDecode(database.logger.getCrypto(),
                                                 new byte[initIOBufferSize]);
            }
        }
    }

    DataFileDefrag defrag() {

        writeLock.lock();

        try {
            cache.saveAll();

            DataFileDefrag dfd = new DataFileDefrag(database, this,
                dataFileName);

            dfd.process();
            close(true);
            cache.clear();

            if (!database.logger.propIncrementBackup) {
                backupFile(true);
            }

            database.schemaManager.setTempIndexRoots(dfd.getIndexRoots());

            try {
                database.logger.log.writeScript(false);
            } finally {
                database.schemaManager.setTempIndexRoots(null);
            }

            database.getProperties().setProperty(
                HsqlDatabaseProperties.hsqldb_script_format,
                database.logger.propScriptFormat);
            database.getProperties().setDBModified(
                HsqlDatabaseProperties.FILES_MODIFIED_NEW);
            database.logger.log.closeLog();
            database.logger.log.deleteLog();
            database.logger.log.renameNewScript();
            renameBackupFile();
            renameDataFile();
            database.getProperties().setDBModified(
                HsqlDatabaseProperties.FILES_NOT_MODIFIED);
            open(false);
            database.schemaManager.setIndexRoots(dfd.getIndexRoots());

            if (database.logger.log.dbLogWriter != null) {
                database.logger.log.openLog();
            }

            database.getProperties().setDBModified(
                HsqlDatabaseProperties.FILES_MODIFIED);

            return dfd;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Used when a row is deleted as a result of some DML or DDL statement.
     * Removes the row from the cache data structures.
     * Adds the file space for the row to the list of free positions.
     */
    public void remove(CachedObject object, TableSpaceManager spaceManager) {

        writeLock.lock();

        try {
            release(object.getPos());
            spaceManager.release(object.getPos(), object.getStorageSize());
        } finally {
            writeLock.unlock();
        }
    }

    public void removePersistence(CachedObject object) {}

    /**
     * Allocates file space for the row. <p>
     *
     * Free space is requested from the block manager if it exists.
     * Otherwise the file is grown to accommodate it.
     */
    public long setFilePos(CachedObject r,
                           TableSpaceManager tableSpaceManager,
                           boolean asBlock) {

        int  rowSize = r.getStorageSize();
        long i       = tableSpaceManager.getFilePosition(rowSize, asBlock);

        r.setPos(i);

        return i;
    }

    public void add(CachedObject object) {

        writeLock.lock();

        try {
            cacheModified = true;

            cache.put(object);

            if (object.getStorageSize() > initIOBufferSize) {
                rowOut.reset(object.getStorageSize());
            }
        } finally {
            writeLock.unlock();
        }
    }

    public int getStorageSize(long i) {

        readLock.lock();

        try {
            CachedObject value = cache.get(i);

            if (value != null) {
                return value.getStorageSize();
            }
        } finally {
            readLock.unlock();
        }

        return readSize(i);
    }

    public void replace(CachedObject object) {

        writeLock.lock();

        try {
            long pos = object.getPos();

            cache.replace(pos, object);
        } finally {
            writeLock.unlock();
        }
    }

    public CachedObject get(CachedObject object, PersistentStore store,
                            boolean keep) {

        readLock.lock();

        long pos;

        try {
            if (object.isInMemory()) {
                if (keep) {
                    object.keepInMemory(true);
                }

                return object;
            }

            pos = object.getPos();

            if (pos < 0) {
                return null;
            }

            object = cache.get(pos);

            if (object != null) {
                if (keep) {
                    object.keepInMemory(true);
                }

                return object;
            }
        } finally {
            readLock.unlock();
        }

        return getFromFile(pos, store, keep);
    }

    public CachedObject get(long pos, int size, PersistentStore store,
                            boolean keep) {

        CachedObject object;

        if (pos < 0) {
            return null;
        }

        readLock.lock();

        try {
            object = cache.get(pos);

            if (object != null) {
                if (keep) {
                    object.keepInMemory(true);
                }

                return object;
            }
        } finally {
            readLock.unlock();
        }

        return getFromFile(pos, size, store, keep);
    }

    public CachedObject get(long pos, PersistentStore store, boolean keep) {

        CachedObject object;

        if (pos < 0) {
            return null;
        }

        readLock.lock();

        try {
            object = cache.get(pos);

            if (object != null) {
                if (keep) {
                    object.keepInMemory(true);
                }

                return object;
            }
        } finally {
            readLock.unlock();
        }

        return getFromFile(pos, store, keep);
    }

    private CachedObject getFromFile(long pos, PersistentStore store,
                                     boolean keep) {

        CachedObject object = null;

        writeLock.lock();

        try {
            object = cache.get(pos);

            if (object != null) {
                if (keep) {
                    object.keepInMemory(true);
                }

                return object;
            }

            for (int j = 0; j < 2; j++) {
                try {
                    RowInputInterface rowInput = readObject(pos);

                    if (rowInput == null) {
                        return null;
                    }

                    object = store.get(rowInput);

                    break;
                } catch (OutOfMemoryError err) {
                    cache.forceCleanUp();
                    System.gc();
                    logSevereEvent(dataFileName + " getFromFile out of mem "
                                   + pos, err);

                    if (j > 0) {
                        throw err;
                    }
                }
            }

            // for text tables with empty rows at the beginning,
            // pos may move forward in readObject
            cache.put(object);

            if (keep) {
                object.keepInMemory(true);
            }

            store.set(object);

            return object;
        } catch (HsqlException e) {
            logSevereEvent(dataFileName + " getFromFile " + pos, e);

            throw e;
        } finally {
            writeLock.unlock();
        }
    }

    private CachedObject getFromFile(long pos, int size,
                                     PersistentStore store, boolean keep) {

        CachedObject object = null;

        writeLock.lock();

        try {
            object = cache.get(pos);

            if (object != null) {
                if (keep) {
                    object.keepInMemory(true);
                }

                return object;
            }

            for (int j = 0; j < 2; j++) {
                try {
                    RowInputInterface rowInput = readObject(pos, size);

                    if (rowInput == null) {
                        return null;
                    }

                    object = store.get(rowInput);

                    break;
                } catch (OutOfMemoryError err) {
                    cache.forceCleanUp();
                    System.gc();
                    logSevereEvent(dataFileName + " getFromFile out of mem "
                                   + pos, err);

                    if (j > 0) {
                        throw err;
                    }
                }
            }

            // for text tables with empty rows at the beginning,
            // pos may move forward in readObject
            cache.put(object);

            if (keep) {
                object.keepInMemory(true);
            }

            store.set(object);

            return object;
        } catch (HsqlException e) {
            logSevereEvent(dataFileName + " getFromFile " + pos, e);

            throw e;
        } finally {
            writeLock.unlock();
        }
    }

    RowInputInterface getRaw(int i) {

        writeLock.lock();

        try {
            return readObject(i);
        } finally {
            writeLock.unlock();
        }
    }

    protected int readSize(long pos) {

        writeLock.lock();

        try {
            dataFile.seek(pos * dataFileScale);

            return dataFile.readInt();
        } catch (IOException e) {
            logSevereEvent("readSize", e, pos);

            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        } finally {
            writeLock.unlock();
        }
    }

    protected RowInputInterface readObject(long pos) {

        try {
            dataFile.seek(pos * dataFileScale);

            int size = dataFile.readInt();

            rowIn.resetRow(pos, size);
            dataFile.read(rowIn.getBuffer(), 4, size - 4);

            return rowIn;
        } catch (IOException e) {
            logSevereEvent("readObject", e, pos);

            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        }
    }

    protected RowInputInterface readObject(long pos, int size) {

        try {
            dataFile.seek(pos * dataFileScale);
            rowIn.resetBlock(pos, size);
            dataFile.read(rowIn.getBuffer(), 0, size);

            return rowIn;
        } catch (IOException e) {
            logSevereEvent("readObject", e, pos);

            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        }
    }

    public void releaseRange(long start, long limit) {

        writeLock.lock();

        try {
            Iterator it = cache.getIterator();

            while (it.hasNext()) {
                CachedObject o   = (CachedObject) it.next();
                long         pos = o.getPos();

                if (pos >= start && pos < limit) {
                    o.setInMemory(false);
                    it.remove();
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    public CachedObject release(long pos) {

        writeLock.lock();

        try {
            return cache.release(pos);
        } finally {
            writeLock.unlock();
        }
    }

    protected void saveRows(CachedObject[] rows, int offset, int count) {

        if (count == 0) {
            return;
        }

        try {
            copyShadow(rows, offset, count);
            setFileModified();

            for (int i = offset; i < offset + count; i++) {
                CachedObject r = rows[i];

                saveRowNoLock(r);

                rows[i] = null;
            }
        } catch (HsqlException e) {
            throw e;
        } catch (Throwable e) {
            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        } finally {
            initBuffers();
        }
    }

    /**
     * Writes out the specified Row. Will write only the Nodes or both Nodes
     * and table row data depending on what is not already persisted to disk.
     */
    public void saveRow(CachedObject row) {

        writeLock.lock();

        try {
            copyShadow(row);
            setFileModified();
            saveRowNoLock(row);
        } catch (Throwable e) {
            logSevereEvent("saveRow", e, row.getPos());

            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        } finally {
            writeLock.unlock();
        }
    }

    public void saveRowOutput(long pos) {

        try {
            dataFile.seek(pos * dataFileScale);
            dataFile.write(rowOut.getOutputStream().getBuffer(), 0,
                           rowOut.getOutputStream().size());
        } catch (IOException e) {
            logSevereEvent("saveRowOutput", e, pos);

            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        }
    }

    protected void saveRowNoLock(CachedObject row) {

        try {
            rowOut.reset();
            row.write(rowOut);
            dataFile.seek(row.getPos() * dataFileScale);
            dataFile.write(rowOut.getOutputStream().getBuffer(), 0,
                           rowOut.getOutputStream().size());
        } catch (IOException e) {
            logSevereEvent("saveRowNoLock", e, row.getPos());

            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        }
    }

    protected void copyShadow(CachedObject[] rows, int offset,
                              int count) throws IOException {

        if (shadowFile != null) {
            long time = cache.saveAllTimer.elapsedTime();

            for (int i = offset; i < offset + count; i++) {
                CachedObject row     = rows[i];
                long         seekpos = row.getPos() * dataFileScale;

                shadowFile.copy(seekpos, row.getStorageSize());
            }

            shadowFile.synch();

            time = cache.saveAllTimer.elapsedTime() - time;

            logDetailEvent("shadow copy [time, size] " + time + " "
                           + shadowFile.getSavedLength());
        }
    }

    protected void copyShadow(CachedObject row) throws IOException {

        if (shadowFile != null) {
            long seekpos = row.getPos() * dataFileScale;

            shadowFile.copy(seekpos, row.getStorageSize());
            shadowFile.synch();
        }
    }

    /**
     *  Saves the *.data file as compressed *.backup.
     *
     * @throws  HsqlException
     */
    void backupFile(boolean newFile) {

        writeLock.lock();

        try {
            if (database.logger.propIncrementBackup) {
                if (fa.isStreamElement(backupFileName)) {
                    deleteBackup();
                }

                return;
            }

            if (fa.isStreamElement(dataFileName)) {
                String filename = newFile
                                  ? dataFileName + Logger.newFileExtension
                                  : dataFileName;

                FileArchiver.archive(filename,
                                     backupFileName + Logger.newFileExtension,
                                     database.logger.getFileAccess(),
                                     FileArchiver.COMPRESSION_ZIP);
            }
        } catch (IOException e) {
            logSevereEvent("backupFile failed", e);

            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        } finally {
            writeLock.unlock();
        }
    }

    void renameBackupFile() {

        writeLock.lock();

        try {
            if (database.logger.propIncrementBackup) {
                deleteBackup();

                return;
            }

            if (fa.isStreamElement(backupFileName + Logger.newFileExtension)) {
                deleteBackup();
                fa.renameElement(backupFileName + Logger.newFileExtension,
                                 backupFileName);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     *  Renames the *.data.new file.
     *
     * @throws  HsqlException
     */
    void renameDataFile() {

        writeLock.lock();

        try {
            if (fa.isStreamElement(dataFileName + Logger.newFileExtension)) {
                deleteFile();
                fa.renameElement(dataFileName + Logger.newFileExtension,
                                 dataFileName);
            }
        } finally {
            writeLock.unlock();
        }
    }

    void deleteFile() {

        writeLock.lock();

        try {

            // first attemp to delete
            fa.removeElement(dataFileName);

            // OOo related code
            if (database.logger.isStoredFileAccess()) {
                return;
            }

            // OOo end
            if (fa.isStreamElement(dataFileName)) {
                this.database.logger.log.deleteOldDataFiles();
                fa.removeElement(dataFileName);

                if (fa.isStreamElement(dataFileName)) {
                    String discardName =
                        FileUtil.newDiscardFileName(dataFileName);

                    fa.renameElement(dataFileName, discardName);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    void deleteBackup() {

        writeLock.lock();

        try {
            if (fa.isStreamElement(backupFileName)) {
                fa.removeElement(backupFileName);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Delta always results in block multiples
     */
    public long enlargeFileSpace(long delta) {

        writeLock.lock();

        try {
            long position = fileFreePosition;

            if (position + delta > maxDataFileSize) {
                logSevereEvent("data file reached maximum size "
                               + this.dataFileName, null);

                throw Error.error(ErrorCode.DATA_FILE_IS_FULL);
            }

            boolean result = dataFile.ensureLength(position + delta);

            if (!result) {
                logSevereEvent("data file cannot be enlarged - disk spacee "
                               + this.dataFileName, null);

                throw Error.error(ErrorCode.DATA_FILE_IS_FULL);
            }

            fileFreePosition += delta;

            return position;
        } finally {
            writeLock.unlock();
        }
    }

    public int capacity() {
        return maxCacheRows;
    }

    public long bytesCapacity() {
        return maxCacheBytes;
    }

    public long getTotalCachedBlockSize() {
        return cache.getTotalCachedBlockSize();
    }

    public long getLostBlockSize() {
        return spaceManager.getLostBlocksSize();
    }

    public long getFreeBlockCount() {
        return spaceManager.freeBlockCount();
    }

    public long getTotalFreeBlockSize() {
        return spaceManager.freeBlockSize();
    }

    public long getFileFreePos() {
        return fileFreePosition;
    }

    public int getCachedObjectCount() {
        return cache.size();
    }

    public int getAccessCount() {
        return cache.incrementAccessCount();
    }

    public String getFileName() {
        return dataFileName;
    }

    public int getDataFileScale() {
        return dataFileScale;
    }

    public boolean hasRowInfo() {
        return hasRowInfo;
    }

    public boolean isFileModified() {
        return fileModified;
    }

    public boolean isModified() {
        return cacheModified;
    }

    public boolean isFileOpen() {
        return dataFile != null;
    }

    protected void setFileModified() {

        try {
            if (!fileModified) {

                // unset saved flag;
                long start = cache.saveAllTimer.elapsedTime();

                cache.saveAllTimer.start();
                dataFile.seek(FLAGS_POS);

                int flags = dataFile.readInt();

                flags = BitMap.unset(flags, FLAG_ISSAVED);

                dataFile.seek(FLAGS_POS);
                dataFile.writeInt(flags);
                dataFile.synch();
                cache.saveAllTimer.stop();
                logDetailEvent("flags set "
                               + cache.saveAllTimer.elapsedTime());

                fileModified = true;
            }
        } catch (Throwable t) {}
    }

    public int getFlags() {

        try {
            dataFile.seek(FLAGS_POS);

            int flags = dataFile.readInt();

            return flags;
        } catch (Throwable t) {}

        return 0;
    }

    public boolean isDataReadOnly() {
        return this.cacheReadonly;
    }

    public RAShadowFile getShadowFile() {
        return shadowFile;
    }

    private void logSevereEvent(String message, Throwable t, long position) {

        if (logEvents) {
            StringBuffer sb = new StringBuffer(message);

            sb.append(' ').append(position);

            message = sb.toString();

            database.logger.logSevereEvent(message, t);
        }
    }

    void logSevereEvent(String message, Throwable t) {

        if (logEvents) {
            database.logger.logSevereEvent(message, t);
        }
    }

    void logInfoEvent(String message) {

        if (logEvents) {
            database.logger.logInfoEvent(message);
        }
    }

    void logDetailEvent(String message) {

        if (logEvents) {
            database.logger.logDetailEvent(message);
        }
    }
}
