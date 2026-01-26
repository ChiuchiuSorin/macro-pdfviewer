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
package com.xwiki.pdfviewer.internal.rest;

import java.util.Base64;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.rest.XWikiRestException;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.pdfviewer.internal.token.DelegatedTokenManager;
import com.xwiki.pdfviewer.rest.PDFMacroResource;

/**
 * Default implementation of {@link PDFMacroResource}.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Named("com.xwiki.pdfviewer.internal.rest.DefaultPDFMacroResource")
@Singleton
public class DefaultPDFMacroResource implements PDFMacroResource
{
    @Inject
    private DelegatedTokenManager tokenManager;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

    @Inject
    private CSRFToken csrf;

    @Override
    public Response getContents(String token, String formToken) throws XWikiRestException
    {
        try {
            String decodedToken = new String(Base64.getDecoder().decode(token));
            if (tokenManager.isInvalid(decodedToken) || !tokenManager.hasAccess(decodedToken) || !csrf.isTokenValid(
                formToken))
            {
                logger.warn("Failed to get content of requested file due to invalid token or restricted rights.");
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            XWikiContext context = contextProvider.get();
            XWiki wiki = context.getWiki();
            AttachmentReference attachRef = tokenManager.getTokenAttachmentReference(decodedToken);
            XWikiDocument wikiDoc = wiki.getDocument(attachRef.getDocumentReference(), context);
            XWikiAttachment attachment = wikiDoc.getAttachment(attachRef.getName());
            return Response.status(Response.Status.OK).entity(attachment.getContentInputStream(context))
                .type("application/pdf").build();
        } catch (Exception e) {
            logger.error("An error occurred while attempting to retrieve file content.", e);
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
