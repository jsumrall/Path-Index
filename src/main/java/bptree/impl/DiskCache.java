package bptree.impl;

import bptree.PageProxyCursor;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.pagecache.PagedFile;
import org.neo4j.io.pagecache.impl.muninn.MuninnPageCache;
import org.neo4j.io.pagecache.monitoring.PageCacheMonitor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;


public class DiskCache {
    //public static int PAGE_SIZE = bptree.Utils.getIdealBlockSize();
    public static int PAGE_SIZE = 8192;
    protected final static String DEFAULT_CACHE_FILE_NAME = "cache.bin";
    protected int recordSize = 9; //TODO What is this?
    protected int max_size_in_mb = 8192;
    protected int maxPages = max_size_in_mb * (1000000 / PAGE_SIZE);
    //protected int maxPages = 8000; //TODO How big should this be?
    protected int pageCachePageSize = PAGE_SIZE;
    protected int recordsPerFilePage = pageCachePageSize / recordSize;
    protected int recordCount = 25 * maxPages * recordsPerFilePage;
    protected int filePageSize = recordsPerFilePage * recordSize;
    protected transient DefaultFileSystemAbstraction fs;
    protected transient MuninnPageCache pageCache;
    public static transient PagedFile pagedFile;
    public File cache_file;
    public static DiskCache singleInstance;

    private DiskCache(File cache_file) {
        try {
            initializePageCache(cache_file);
            this.cache_file = cache_file;
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
    public static PageProxyCursor getCursor(long id, int lockType) throws IOException {
        return new BasicPageCursor(singleInstance, id, lockType);
        //return new ZLIBPageCursor(singleInstance, id, lockType);
    }

    public static DiskCache temporaryDiskCache(){
        singleInstance = temporaryDiskCache(DEFAULT_CACHE_FILE_NAME);
        return singleInstance;
    }

    public static DiskCache temporaryDiskCache(String filename){
        File cache_file = new File(filename);
        cache_file.deleteOnExit();
        singleInstance = new DiskCache(cache_file);
        return singleInstance;
    }

    public static DiskCache persistentDiskCache(){
        singleInstance =  persistentDiskCache(DEFAULT_CACHE_FILE_NAME);
        return singleInstance;
    }

    public static DiskCache persistentDiskCache(String filename){
        singleInstance = new DiskCache(new File(filename));
        return singleInstance;
    }

    private void initializePageCache(File page_cache_file) throws IOException {
        fs = new DefaultFileSystemAbstraction();
        pageCache = new MuninnPageCache(fs, maxPages, pageCachePageSize, PageCacheMonitor.NULL);
        pagedFile = pageCache.map(page_cache_file, filePageSize);
    }

    public ByteBuffer readPage(long id) {
        byte[] byteArray = new byte[0];
        try (PageProxyCursor cursor = DiskCache.getCursor(NodeTree.rootNodeId, PagedFile.PF_EXCLUSIVE_LOCK)) {
                    byteArray = new byte[cursor.getSize()];
                    cursor.getBytes(byteArray);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return ByteBuffer.wrap(byteArray);
    }
    public ByteBuffer readPage(PageProxyCursor cursor) {
        cursor.setOffset(0);
        byte[] byteArray = new byte[0];
        byteArray = new byte[cursor.getSize()];
        cursor.getBytes(byteArray);
        return ByteBuffer.wrap(byteArray);
    }

    public void writePage(long id, byte[] bytes) {
        try (PageProxyCursor cursor = DiskCache.getCursor(id, PagedFile.PF_EXCLUSIVE_LOCK)) {
                    // perform read or write operations on the page
                    cursor.putBytes(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getMaxNumberOfPages(){
        return pageCache.maxCachedPages();
    }

    public int cache_size(){
        return (int)(cache_file.length() / 1000000l);
    }

    public PagedFile getPagedFile(){
        return pagedFile;
    }
    public void shutdown() throws IOException {
        pagedFile.close();
        pageCache.close();
    }
}
