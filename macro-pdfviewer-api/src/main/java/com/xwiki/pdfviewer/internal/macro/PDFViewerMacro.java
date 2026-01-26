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
package com.xwiki.pdfviewer.internal.macro;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.script.ScriptContext;

import org.apache.commons.lang.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.template.Template;
import org.xwiki.template.TemplateManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.licensing.Licensor;
import com.xwiki.pdfviewer.internal.MJSMimeTypeRegistrar;
import com.xwiki.pdfviewer.internal.token.DelegatedTokenManager;
import com.xwiki.pdfviewer.macro.PDFFile;
import com.xwiki.pdfviewer.macro.PDFViewerMacroParameters;

/**
 * View PDF attachments inside wiki pages.
 *
 * @version $Id$
 * @since 2.3
 */
@Component
@Named("pdfviewer")
@Singleton
public class PDFViewerMacro extends AbstractMacro<PDFViewerMacroParameters>
{
    private static final String PDF_SEPARATOR = "(?<=\\.pdf),";

    private static final String PDF_CONTENT_FORMAT = "%s/rest/pdfmacro/contents?access_token=%s";

    private final List<String> delegatedRightsValues = List.of("1", "true", "yes");

    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    private TemplateManager templateManager;

    @Inject
    @Named("resource/standardURL")
    private EntityReferenceResolver<String> urlEntityReferenceResolver;

    @Inject
    @Named("current")
    private EntityReferenceResolver<String> entityReferenceResolver;

    @Inject
    private ScriptContextManager scriptContextManager;

    @Inject
    private Licensor licensor;

    @Inject
    private Provider<XWikiContext> wikiContextProvider;

    @Inject
    private MJSMimeTypeRegistrar mjsMimeTypeRegistrar;

    @Inject
    private DelegatedTokenManager tokenManager;

    /**
     * Create and initialize the descriptor of the macro.
     */
    public PDFViewerMacro()
    {
        super("PDF Viewer", "View PDF attachments inside wiki pages without downloading or importing them.",
            PDFViewerMacroParameters.class);
        setDefaultCategories(Collections.singleton(DEFAULT_CATEGORY_CONTENT));
    }

    @Override
    public List<Block> execute(PDFViewerMacroParameters parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        DocumentReference licenseDoc =
            new DocumentReference(wikiContextProvider.get().getWikiId(), List.of("PDFViewer", "Code"), "WebHome");
        if (!licensor.hasLicensure(licenseDoc)) {
            licenceError(context);
        }

        try {
            mjsMimeTypeRegistrar.maybeRegisterMJSMimeType();
            Template customTemplate = this.templateManager.getTemplate("pdfviewer/pdfviewer.vm");
            List<String> allFiles = new ArrayList<>();
            if (StringUtils.isNotBlank(parameters.getFile())) {
                String[] files = parameters.getFile().split(PDF_SEPARATOR);
                Collections.addAll(allFiles, files);
            }
            if (StringUtils.isNotBlank(parameters.getFileFromExternalUrl())) {
                String[] filesFromExternalUrl = parameters.getFileFromExternalUrl().split(PDF_SEPARATOR);
                Collections.addAll(allFiles, filesFromExternalUrl);
            }
            List<PDFFile> resourcesList = new ArrayList<>();
            boolean delegatedRights = delegatedRightsValues.contains(parameters.getAsAuthor().toLowerCase());
            for (String file : allFiles) {
                resourcesList.add(getPDFFile(file, delegatedRights, parameters.getDocument()));
            }
            this.bindValues(parameters, resourcesList);

            return this.templateManager.execute(customTemplate).getChildren();
        } catch (Exception e) {
            throw new MacroExecutionException("Failed to render the PDF Viewer template.", e);
        }
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    private List<Block> licenceError(MacroTransformationContext context)
    {
        return Collections.singletonList(new MacroBlock("missingLicenseMessage",
            Collections.singletonMap("extensionName", "proMacros.extension.name"), null, context.isInline()));
    }

    private void bindValues(PDFViewerMacroParameters parameters, List<PDFFile> resourcesList)
    {
        ScriptContext scriptContext = scriptContextManager.getScriptContext();

        scriptContext.setAttribute("params", getTemplateParameters(parameters), ScriptContext.ENGINE_SCOPE);
        scriptContext.setAttribute("files", resourcesList, ScriptContext.ENGINE_SCOPE);
    }

    private PDFFile getPDFFile(String pdfFileReference, boolean delegatedRights, String ownerDocumentReference)
        throws XWikiException
    {
        //  If the url is not directly specified, the attachment reference can be taken either directly from the file
        //  macro parameter, since an attachment picker is used, or by using both the file and document parameters,
        //  for macros added in xwiki versions before 11.5.
        if (pdfFileReference.startsWith("http://") || pdfFileReference.startsWith("https://")) {
            return handleExternalURL(pdfFileReference, delegatedRights);
        } else {
            return handleInternalAttachment(pdfFileReference, delegatedRights, ownerDocumentReference);
        }
    }

    private PDFFile handleExternalURL(String pdfURL, boolean delegatedRights) throws XWikiException
    {
        AttachmentReference attachmentReference =
            new AttachmentReference(urlEntityReferenceResolver.resolve(pdfURL, EntityType.ATTACHMENT));

        //  If the attachment reference name is the same as the given url, it means that the url directs to a file
        //  outside of XWiki instance and there is no need to check the user view right or delegated rights. We still
        //  send the attachment reference to be able to extract the name for it to be displayed in a tab in the case
        //  of multiple attachments.
        PDFFile pdfFile = new PDFFile();
        pdfFile.setAttachmentReference(attachmentReference);
        if (attachmentReference.getName().equals(pdfURL)) {
            pdfFile.setUrl(pdfURL);
        } else if (attachmentExists(attachmentReference) && hasViewRights(attachmentReference, delegatedRights,
            pdfFile))
        {
            if (pdfFile.areViewRightsDelegated()) {
                pdfFile.setUrl(getTokenURL(attachmentReference));
            } else {
                pdfFile.setUrl(pdfURL);
            }
        }
        return pdfFile;
    }

    private PDFFile handleInternalAttachment(String pdfFileReference, boolean delegatedRights,
        String ownerDocumentReference) throws XWikiException
    {
        PDFFile pdfFile = new PDFFile();
        XWikiDocument ownerDocument =
            getOwnerDocumentFromParameters(ownerDocumentReference, pdfFileReference, delegatedRights, pdfFile);
        XWikiAttachment attachment = ownerDocument.getAttachment(pdfFileReference);

        // If the attachment is not in the current or given document, the file parameter is used as a full reference.
        if (attachment == null) {
            AttachmentReference attachmentReference =
                new AttachmentReference(entityReferenceResolver.resolve(pdfFileReference, EntityType.ATTACHMENT));
            return getPDFFile(attachmentReference, delegatedRights);
        } else {
            pdfFile.setAttachmentReference(attachment.getReference());
            if (pdfFile.areViewRightsDelegated() && pdfFile.hasViewRights()) {
                pdfFile.setUrl(getTokenURL(attachment.getReference()));
            } else if (pdfFile.hasViewRights()) {
                XWikiContext wikiContext = wikiContextProvider.get();
                String url = ownerDocument.getAttachmentURL(attachment.getFilename(), wikiContext);
                pdfFile.setUrl(url);
            }
            return pdfFile;
        }
    }

    private PDFFile getPDFFile(AttachmentReference attachmentReference, boolean delegatedRights) throws XWikiException
    {
        PDFFile pdfFile = new PDFFile();
        if (attachmentExists(attachmentReference) && hasViewRights(attachmentReference, delegatedRights, pdfFile)) {
            pdfFile.setAttachmentReference(attachmentReference);
            pdfFile.setUrl(buildAttachmentURL(attachmentReference, pdfFile));
        }
        return pdfFile;
    }

    private XWikiDocument getOwnerDocumentFromParameters(String ownerDocumentReference, String pdfFileReference,
        boolean delegatedRights, PDFFile pdfFile) throws XWikiException
    {
        // For backwards compatibility with the macros added in older versions, first it is checked if the file name
        // specified exists on the defined document or on the current one before considering the file parameter as the
        // full reference.
        XWikiContext wikiContext = wikiContextProvider.get();
        XWikiDocument attachmentDocument = wikiContext.getDoc();
        if ((ownerDocumentReference == null || ownerDocumentReference.isEmpty()) && authorizationManager.hasAccess(
            Right.VIEW, wikiContext.getUserReference(), attachmentDocument.getDocumentReference()))
        {
            pdfFile.setHasViewRights(true);
        } else {
            DocumentReference givenDocumentReference =
                new DocumentReference(entityReferenceResolver.resolve(ownerDocumentReference, EntityType.DOCUMENT));
            AttachmentReference attachmentReference = new AttachmentReference(
                this.entityReferenceResolver.resolve(pdfFileReference, EntityType.ATTACHMENT, givenDocumentReference));
            if (attachmentExists(attachmentReference) && hasViewRights(attachmentReference, delegatedRights, pdfFile)) {
                attachmentDocument = wikiContext.getWiki().getDocument(givenDocumentReference, wikiContext);
            }
        }
        return attachmentDocument;
    }

    private boolean hasViewRights(AttachmentReference attachmentReference, boolean delegatedRights, PDFFile pdfFile)
        throws XWikiException
    {
        XWikiContext wikiContext = wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        DocumentReference documentReference = attachmentReference.getDocumentReference();
        if (documentReference == null || !wiki.exists(documentReference, wikiContext)) {
            return false;
        }

        if (hasViewAccess(wikiContext.getUserReference(), documentReference)) {
            pdfFile.setHasViewRights(true);
            return true;
        }

        XWikiDocument sdoc = (XWikiDocument) wikiContext.get(XWikiDocument.CKEY_SDOC);
        if (!delegatedRights) {
            tokenManager.clearToken(attachmentReference, sdoc.getDocumentReference());
            return false;
        }

        return hasDelegatedViewRights(attachmentReference, sdoc, pdfFile);
    }

    private boolean hasDelegatedViewRights(AttachmentReference attachmentReference, XWikiDocument sdoc, PDFFile pdfFile)
    {
        DocumentReference author = sdoc.getContentAuthorReference();
        boolean hasViewRights = hasViewAccess(author, attachmentReference.getDocumentReference());
        pdfFile.setDelegatedViewRights(true);
        pdfFile.setHasViewRights(hasViewRights);
        if (!hasViewRights) {
            tokenManager.clearToken(attachmentReference, sdoc.getDocumentReference());
        }
        return hasViewRights;
    }

    private boolean hasViewAccess(DocumentReference user, DocumentReference target)
    {
        return authorizationManager.hasAccess(Right.VIEW, user, target);
    }

    private boolean attachmentExists(AttachmentReference attachmentReference) throws XWikiException
    {
        XWikiContext wikiContext = wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        XWikiDocument wikiDocument = wiki.getDocument(attachmentReference.getDocumentReference(), wikiContext);
        return wikiDocument.getAttachment(attachmentReference.getName()) != null;
    }

    private String buildAttachmentURL(AttachmentReference attachmentReference, PDFFile pdfFile) throws XWikiException
    {
        if (pdfFile.areViewRightsDelegated()) {
            return getTokenURL(attachmentReference);
        } else {
            XWikiContext wikiContext = this.wikiContextProvider.get();
            DocumentReference parentDocRef = new DocumentReference(attachmentReference.getParent());
            XWikiDocument attachmentDocument = wikiContext.getWiki().getDocument(parentDocRef, wikiContext);
            return attachmentDocument.getAttachmentURL(attachmentReference.getName(), wikiContext);
        }
    }

    private String getTokenURL(AttachmentReference attachmentReference)
    {
        XWikiContext wikiContext = wikiContextProvider.get();
        XWikiDocument sdoc = (XWikiDocument) wikiContext.get(XWikiDocument.CKEY_SDOC);
        DocumentReference currentAuthor = sdoc.getContentAuthorReference();
        String tokenId = tokenManager.getToken(currentAuthor, attachmentReference, sdoc.getDocumentReference());
        String encodedToken = Base64.getUrlEncoder().encodeToString(tokenId.getBytes());
        return String.format(PDF_CONTENT_FORMAT, wikiContext.getRequest().getContextPath(), encodedToken);
    }

    private Map<String, String> getTemplateParameters(PDFViewerMacroParameters parameters)
    {
        // Height and width are handled differently, as the height value can only be represented in pixels (as an
        // int), while the width value can be represented either in pixels or as a percentage (as a String).
        Map<String, String> parametersMap = new HashMap<>();
        parametersMap.put("width", parameters.getWidth());
        parametersMap.put("height", String.valueOf(parameters.getHeight()));
        return parametersMap;
    }
}
