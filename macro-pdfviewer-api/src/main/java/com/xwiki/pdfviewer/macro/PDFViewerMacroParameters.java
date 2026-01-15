/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.pdfviewer.macro;

import org.xwiki.properties.annotation.PropertyAdvanced;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyDisplayType;
import org.xwiki.stability.Unstable;

import com.xwiki.pdfviewer.PDFResourceReference;

/**
 * Parameters for PDF Viewer Macro.
 *
 * @version $Id$
 * @since 2.3
 */
public class PDFViewerMacroParameters
{
    /**
     * One or multiple PDF files to be viewed. In case multiple files are defined, the {@code String} will contain a
     * list with comma as delimiter. Use the full attachment reference to specify a PDF file, an absolute URL or only
     * the name of the attachment when is used along with the document parameter.
     */
    private String file;

    /**
     * One or multiple PDF files to be viewed, imported from external URLs. In case of multiple files, the
     * {@code String} contains a comma-separated list of URLs.
     *
     * @since 2.6.3
     */
    private String fileFromExternalUrl;

    /**
     * The viewer width. Uses a percentage value, for example: 25%, 50%, 100%.
     */
    private String width = "100%";

    /**
     * The viewer height, in pixels.
     */
    private int height = 1000;

    /**
     * String reference of the document that contains one or multiple files. In case of each file, it can be ignored if
     * that file is an URL or the computed attachment reference does not actually exists.
     */
    private String document;

    /**
     * If this value is true (or 1 or yes) and the user has no access to the document to which the PDF file is attached,
     * the PDF file could still be viewed on behalf of the author of the document containing the macro (if that author
     * has access to the containing document).
     *
     * TODO: change the type to boolean and add a migration for the old values.
     */
    private String asAuthor = "0";

    /**
     * @return one or a list of PDF files
     */
    public String getFile()
    {
        return this.file;
    }

    /**
     * Set the value of one or multiple files. One file is represented by using a full attachment reference, an absolute
     * URL or simply the file name.
     * 
     * @param file one or a list of PDF files
     */
    @PropertyDisplayType(PDFResourceReference.class)
    public void setFile(String file)
    {
        this.file = file;
    }

    /**
     * @return one or a comma-separated list of PDF files from external URLs
     * @since 2.6.3
     */
    @Unstable
    public String getFileFromExternalUrl()
    {
        return fileFromExternalUrl;
    }

    /**
     * Set one or multiple PDF files from external URLs.
     *
     * @param fileFromExternalUrl a single URL or a comma-separated list of URLs
     * @since 2.6.3
     */
    @Unstable
    public void setFileFromExternalUrl(String fileFromExternalUrl)
    {
        this.fileFromExternalUrl = fileFromExternalUrl;
    }

    /**
     * @return the display width of the PDF file
     */
    public String getWidth()
    {
        return width;
    }

    /**
     * Set the display width of the PDF file.
     * 
     * @param width display width of the PDF file
     */
    @PropertyDescription("The viewer width, defined as a percentage (e.g. 50%, 100%). If not defined, the default "
        + "value will be used")
    public void setWidth(String width)
    {
        this.width = width;
    }

    /**
     * @return the display height of the PDF file
     */
    public int getHeight()
    {
        return height;
    }

    /**
     * Set the display height of the PDF file.
     * 
     * @param height display height of the PDF file
     */
    @PropertyDescription("The viewer height, in pixels. If not defined, the default value will be used")
    public void setHeight(int height)
    {
        this.height = height;
    }

    /**
     * @return the reference of the document that contains the PDF files to be viewed
     */
    public String getDocument()
    {
        return document;
    }

    /**
     * Set the reference of the document that contains the PDF files to be viewed.
     * 
     * @param document reference of the document that contains the PDF files
     */
    @PropertyAdvanced
    @PropertyDescription("Reference to the XWiki document to which the file is attached. If not defined, the current "
        + "document or only the file parameter is used. If file argument is an absolute URL or the attachment does "
        + "not exists, this argument is ignored. The same is applied for each defined file.")
    public void setDocument(String document)
    {
        this.document = document;
    }

    /**
     * @return whether to access the PDF file on behalf of the last author of the page calling the PDF Viewer macro
     *         (using their access rights) rather than on behalf of the current user (using the access rights of the
     *         current user)
     */
    public String getAsAuthor()
    {
        return asAuthor;
    }

    /**
     * Set whether to access the PDF file on behalf of the last author of the page calling the PDF Viewer macro (using
     * their access rights) rather than on behalf of the current user (using the access rights of the current user).
     * 
     * @param asAuthor true (or 1 or yes) if the view right of the PDF file document should be delegated to the users
     *            that requests to see it through the macro, false (or 0 or no) otherwise
     */
    @PropertyAdvanced
    @PropertyDescription("If this value is true (or 1 or yes) and the viewing user has no access to the document "
        + "containing the PDF file, the PDF file could still be viewed on behalf of your view right (if you have view "
        + "right on the containing document).")
    public void setAsAuthor(String asAuthor)
    {
        this.asAuthor = asAuthor;
    }
}
