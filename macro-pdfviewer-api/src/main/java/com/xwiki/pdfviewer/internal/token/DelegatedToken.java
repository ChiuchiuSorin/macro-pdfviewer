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
package com.xwiki.pdfviewer.internal.token;

import java.security.SecureRandom;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;

/**
 * Token used in order to authenticate requests done to gain an attachment content.
 *
 * @version $Id$
 * @since 2.7
 */
public class DelegatedToken
{
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DocumentReference user;

    private final AttachmentReference fileReference;

    private final DocumentReference macroOrigin;

    private final int randomNumber;

    DelegatedToken(DocumentReference user, AttachmentReference file, DocumentReference macroOrigin)
    {
        this.user = user;
        this.fileReference = file;
        this.macroOrigin = macroOrigin;
        this.randomNumber = Math.abs(SECURE_RANDOM.nextInt());
    }

    /**
     * @return the user corresponding to this token
     */
    public DocumentReference getUser()
    {
        return this.user;
    }

    /**
     * @return the id of the file corresponding to this token
     */
    public AttachmentReference getFileReference()
    {
        return this.fileReference;
    }

    /**
     * @return the macro origin corresponding to this token
     */
    public DocumentReference getMacroOrigin()
    {
        return macroOrigin;
    }

    @Override
    public String toString()
    {
        return String.format("wopi_%s_%s_%s_%s", this.user.toString(), this.fileReference.toString(),
            this.getMacroOrigin().toString(), this.randomNumber);
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder().append(this.getUser()).append(this.getFileReference().toString())
            .append(this.getMacroOrigin().toString()).append(this.getRandomNumber()).toHashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DelegatedToken)) {
            return false;
        }
        DelegatedToken other = (DelegatedToken) obj;
        EqualsBuilder builder = new EqualsBuilder();

        builder.append(this.getUser(), other.getUser());
        builder.append(this.getFileReference(), other.getFileReference());
        builder.append(this.getMacroOrigin(), other.getMacroOrigin());
        builder.append(this.getRandomNumber(), other.getRandomNumber());

        return builder.build();
    }

    private int getRandomNumber()
    {
        return this.randomNumber;
    }
}

