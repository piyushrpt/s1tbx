/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.s1tbx.io;

import com.bc.ceres.core.VirtualDir;
import org.esa.s1tbx.io.imageio.ImageIOFile;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.dataop.downloadable.XMLSupport;
import org.esa.snap.core.util.Guardian;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.datamodel.metadata.AbstractMetadataIO;
import org.esa.snap.engine_utilities.gpf.ReaderUtils;
import org.esa.snap.engine_utilities.util.ZipUtils;
import org.jdom2.Document;
import org.jdom2.Element;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/**
 * This class represents a product directory.
 */
public abstract class XMLProductDirectory {

    private VirtualDir productDir = null;
    private final String baseName;
    private File baseDir;
    private String rootFolder = null;
    protected Document xmlDoc = null;

    private boolean isSLC = false;

    protected transient final Map<String, ImageIOFile> bandImageFileMap = new TreeMap<>();
    protected transient final Map<Band, ImageIOFile.BandInfo> bandMap = new HashMap<>(3);

    protected XMLProductDirectory(final File inputFile) {
        Guardian.assertNotNull("inputFile", inputFile);

        if (ZipUtils.isZip(inputFile)) {
            productDir = VirtualDir.create(inputFile);
            baseDir = inputFile;
            baseName = inputFile.getName();
        } else {
            productDir = VirtualDir.create(inputFile.getParentFile());
            baseDir = inputFile.getParentFile();
            baseName = inputFile.getParentFile().getName();
        }
    }

    public final String getRootFolder() {
        if (rootFolder != null)
            return rootFolder;
        try {
            if (productDir.isCompressed()) {
                rootFolder = ZipUtils.getRootFolder(baseDir, getHeaderFileName());
            } else {
                rootFolder = "";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rootFolder;
    }

    protected String getRelativePathToImageFolder() {
        return getRootFolder();
    }

    public void readProductDirectory() throws IOException {
        xmlDoc = XMLSupport.LoadXML(getInputStream(getRootFolder() + getHeaderFileName()));
    }

    protected abstract String getHeaderFileName();

    protected abstract void addImageFile(final String imgPath, final MetadataElement newRoot) throws IOException;

    public boolean isSLC() {
        return isSLC;
    }

    protected void setSLC(boolean flag) {
        isSLC = flag;
    }

    protected boolean isCompressed() {
        return productDir.isCompressed();
    }

    protected final void findImages(final String parentPath, final MetadataElement newRoot) throws IOException {
        String[] listing;
        try {
            listing = productDir.list(parentPath);
        } catch (FileNotFoundException e) {
            listing = null;
        }
        if (listing != null) {
            for (String fileName : listing) {
                addImageFile(parentPath + fileName, newRoot);
            }
        }
    }

    protected String getBandFileNameFromImage(final String imgPath) {
        return imgPath.substring(imgPath.lastIndexOf('/') + 1, imgPath.length()).toLowerCase();
    }

    protected Dimension getBandDimensions(final MetadataElement newRoot, final String bandMetadataName) {
        final MetadataElement absRoot = newRoot.getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
        final MetadataElement bandMetadata = absRoot.getElement(bandMetadataName);
        final int width, height;
        if(bandMetadata != null) {
            width = bandMetadata.getAttributeInt(AbstractMetadata.num_samples_per_line);
            height = bandMetadata.getAttributeInt(AbstractMetadata.num_output_lines);
        } else {
            width = absRoot.getAttributeInt(AbstractMetadata.num_samples_per_line);
            height = absRoot.getAttributeInt(AbstractMetadata.num_output_lines);
        }
        return new Dimension(width, height);
    }

    protected void findImages(final MetadataElement newRoot) throws IOException {
        final String parentPath = getRelativePathToImageFolder();
        findImages(parentPath, newRoot);
    }

    public ImageIOFile.BandInfo getBandInfo(final Band destBand) {
        return bandMap.get(destBand);
    }

    public void close() throws IOException {
        final Set<String> keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (String key : keys) {
            final ImageIOFile img = bandImageFileMap.get(key);
            img.close();
        }
    }

    protected abstract void addBands(final Product product);

    protected abstract void addGeoCoding(final Product product);

    protected abstract void addTiePointGrids(final Product product);

    protected abstract void addAbstractedMetadataHeader(final MetadataElement root) throws IOException;

    protected abstract String getProductName();

    protected abstract String getProductType();

    protected String getProductDescription() {
        return "";
    }

    protected MetadataElement addMetaData() throws IOException {
        final MetadataElement root = new MetadataElement(Product.METADATA_ROOT_NAME);
        final Element rootElement = xmlDoc.getRootElement();
        AbstractMetadataIO.AddXMLMetadata(rootElement, AbstractMetadata.addOriginalProductMetadata(root));

        addAbstractedMetadataHeader(root);

        return root;
    }

    protected String[] listFiles(final String path) throws IOException {
        try {
            final String[] listing = productDir.list(path);
            final List<String> files = new ArrayList<>(listing.length);
            for (String listEntry : listing) {
                if (!isDirectory(path + '/' + listEntry)) {
                    files.add(listEntry);
                }
            }
            return files.toArray(new String[files.size()]);
        } catch (Exception e) {
            throw new IOException("Product is corrupt or incomplete\n"+e.getMessage());
        }
    }

    private boolean isDirectory(final String path) throws IOException {
        if (productDir.isCompressed()) {
            if (path.contains(".")) {
                int sepIndex = path.lastIndexOf('/');
                int dotIndex = path.lastIndexOf('.');
                return dotIndex < sepIndex;
            } else {
                final ZipFile productZip = new ZipFile(baseDir, ZipFile.OPEN_READ);

                final Optional result = productZip.stream()
                        .filter(ze -> ze.isDirectory()).filter(ze -> ze.getName().equals(path)).findFirst();
                return result.isPresent();
            }
        } else {
            return productDir.getFile(path).isDirectory();
        }
    }

    protected File getFile(final String path) throws IOException {
        return productDir.getFile(path);
    }

    public boolean exists(final String path) {
        return productDir.exists(path);
    }

    public InputStream getInputStream(final String path) throws IOException {
        InputStream inStream = productDir.getInputStream(path);
        if(inStream == null) {
            throw new IOException("Product is corrupt or incomplete: unreadable "+path);
        }
        return inStream;
    }

    protected File getBaseDir() {
        return baseDir;
    }

    protected String getBaseName() {
        return baseName;
    }

    public Product createProduct() throws Exception {

        final MetadataElement newRoot = addMetaData();
        findImages(newRoot);

        final MetadataElement absRoot = newRoot.getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
        final int sceneWidth = absRoot.getAttributeInt(AbstractMetadata.num_samples_per_line);
        final int sceneHeight = absRoot.getAttributeInt(AbstractMetadata.num_output_lines);

        final Product product = new Product(getProductName(), getProductType(), sceneWidth, sceneHeight);
        updateProduct(product, newRoot);

        addBands(product);
        addGeoCoding(product);
        addTiePointGrids(product);

        product.setName(getProductName());
        product.setProductType(getProductType());
        product.setDescription(getProductDescription());

        ReaderUtils.addMetadataIncidenceAngles(product);
        ReaderUtils.addMetadataProductSize(product);

        return product;
    }

    protected void updateProduct(final Product product, final MetadataElement newRoot) {
        final MetadataElement root = product.getMetadataRoot();
        for(MetadataElement elem : newRoot.getElements()) {
            root.addElement(elem);
        }

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);

        product.setStartTime(absRoot.getAttributeUTC(AbstractMetadata.first_line_time));
        product.setEndTime(absRoot.getAttributeUTC(AbstractMetadata.last_line_time));

        product.setProductType(absRoot.getAttributeString(AbstractMetadata.PRODUCT_TYPE));
        product.setDescription(absRoot.getAttributeString(AbstractMetadata.SPH_DESCRIPTOR));
    }
}
