/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.vfs2.provider;

import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;

import java.util.Map;

/**
 * A {@link FileProvider} that handles physical files, such as the files in a local fs, or on an FTP server. An
 * originating file system cannot be layered on top of another file system.
 */
public abstract class AbstractOriginatingFileProvider extends AbstractFileProvider {

    /**
     * This holds client connect timeout for the FileProvider classes which extend from this
     * abstract class. Currently this is only used for ftp and ftps. Timeout should be specified
     * for ftp or ftps urls in milliseconds as shown below.
     * <p/>
     * e.g:  ftp://admin":password@"localhost/in2\?transport.vfs.ConnectTimeout=1000
     */
    protected Integer defaultTimeout = null;

    private final static String CONNECT_TIMEOUT = "transport.vfs.ConnectTimeout";

    public AbstractOriginatingFileProvider() {
        super();
    }

    /**
     * Locates a file object, by absolute URI.
     *
     * @param baseFile The base file object.
     * @param uri The URI of the file to locate
     * @param fileSystemOptions The FileSystem options.
     * @return The located FileObject
     * @throws FileSystemException if an error occurs.
     */
    @Override
    public FileObject findFile(final FileObject baseFile, final String uri, final FileSystemOptions fileSystemOptions)
            throws FileSystemException {
        Integer timeout = null;
        final Map<String, String> queryParam = UriParser.extractQueryParams(uri);//Extracting Connect Timeout from uri
        String strConnectTimeout = queryParam.get(CONNECT_TIMEOUT);
        if (strConnectTimeout != null) {
            try {
                timeout = Integer.valueOf(strConnectTimeout);
            } catch (NumberFormatException nfe) {
                getLogger().warn("value of transport.vfs.ConnectTimeout param " + strConnectTimeout + " is invalid "
                        + "so the time out does not applied" );
                timeout = null; //timeout not set
            }
        }
        // Parse the URI
        final FileName name;
        try {
            name = parseUri(baseFile != null ? baseFile.getName() : null, uri);
        } catch (final FileSystemException exc) {
            throw new FileSystemException("vfs.provider/invalid-absolute-uri.error", uri, exc);
        }

        // Locate the file
        return findFile(name, fileSystemOptions, timeout);
    }

    /**
     * Locates a file from its parsed URI.
     *
     * @param name The file name.
     * @param fileSystemOptions FileSystem options.
     * @return A FileObject associated with the file.
     * @throws FileSystemException if an error occurs.
     */
    protected FileObject findFile(final FileName name, final FileSystemOptions fileSystemOptions)
            throws FileSystemException {
        return findFile(name, fileSystemOptions, null);
    }

    /**
     * Locates a file from its parsed URI.Can be used to set  default timeout for ftp and ftps
     * connections.
     *
     * @param name The file name.
     * @param fileSystemOptions FileSystem options.
     * @return A FileObject associated with the file.
     * @throws FileSystemException if an error occurs.
     */
    protected FileObject findFile(final FileName name, final FileSystemOptions fileSystemOptions, Integer defaultTimeout)
            throws FileSystemException {
        // Check in the cache for the file system
        final FileName rootName = getContext().getFileSystemManager().resolveName(name, FileName.ROOT_PATH);

        FileSystem fs = getFileSystem(rootName, fileSystemOptions, defaultTimeout);
        FileObject testFile = fs.resolveFile(name);
        try {
            testFile.exists();
        } catch (FileSystemException e) {
            ((DefaultFileSystemManager) getContext().getFileSystemManager()).closeCachedFileSystem(name.getURI(), fileSystemOptions);
            fs = getFileSystem(rootName, fileSystemOptions, defaultTimeout);
            testFile = fs.resolveFile(name);
        }

        // Locate the file
        // return fs.resolveFile(name.getPath());
        return testFile;
    }

    /**
     * Returns the FileSystem associated with the specified root.
     *
     * @param rootName The root path.
     * @param fileSystemOptions The FileSystem options.
     * @return The FileSystem.
     * @throws FileSystemException if an error occurs.
     * @since 2.0
     */
    protected synchronized FileSystem getFileSystem(final FileName rootName, final FileSystemOptions
            fileSystemOptions)
            throws FileSystemException {
        return getFileSystem(rootName, fileSystemOptions, null);
    }

    /**
     * Returns the FileSystem associated with the specified root. Facilitate timeout option.
     *
     * @param rootName The root path.
     * @param fileSystemOptions The FileSystem options.
     * @return The FileSystem.
     * @throws FileSystemException if an error occurs.
     * @since 2.0
     */
    protected synchronized FileSystem getFileSystem(final FileName rootName, final FileSystemOptions
            fileSystemOptions, Integer defaultTimeout)
            throws FileSystemException {
        this.defaultTimeout = defaultTimeout;
        FileSystem fs = findFileSystem(rootName, fileSystemOptions);
        if (fs == null) {
            // Need to create the file system, and cache it
            fs = doCreateFileSystem(rootName, fileSystemOptions);
            addFileSystem(rootName, fs);
        }
        return fs;
    }

    /**
     * Creates a {@link FileSystem}. If the returned FileSystem implements {@link VfsComponent}, it will be initialised.
     *
     * @param rootName The name of the root file of the file system to create.
     * @param fileSystemOptions The FileSystem options.
     * @return The FileSystem.
     * @throws FileSystemException if an error occurs.
     */
    protected abstract FileSystem doCreateFileSystem(final FileName rootName, final FileSystemOptions fileSystemOptions)
            throws FileSystemException;
}
