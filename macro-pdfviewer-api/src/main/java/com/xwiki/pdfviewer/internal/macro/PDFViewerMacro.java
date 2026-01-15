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
            for (String file : allFiles) {
                resourcesList.add(getPDFFile(file, parameters.getAsAuthor(), parameters.getDocument()));
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

    private boolean hasViewRights(DocumentReference documentReference, String delegatedRights, PDFFile pdfFile)
        throws XWikiException
    {
        XWikiContext wikiContext = wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        if (documentReference != null && wiki.exists(documentReference, wikiContext)) {
            if (this.authorizationManager.hasAccess(Right.VIEW, wikiContext.getUserReference(), documentReference)) {
                return true;
            } else if (delegatedRightsValues.contains(delegatedRights.toLowerCase())) {
                XWikiDocument sdoc = (XWikiDocument) wikiContext.get(XWikiDocument.CKEY_SDOC);
                DocumentReference currentAuthor = sdoc.getContentAuthorReference();
                boolean hasViewRights = authorizationManager.hasAccess(Right.VIEW, currentAuthor, documentReference);
                if (pdfFile != null) {
                    pdfFile.setDelegatedViewRights(hasViewRights);
                }
                return hasViewRights;
            }
        }
        return false;
    }

    private PDFFile getPDFFile(String pdfFileReference, String delegatedRights, String ownerDocumentReference)
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

    private PDFFile handleExternalURL(String pdfURL, String delegatedRights) throws XWikiException
    {
        AttachmentReference attachmentReference =
            new AttachmentReference(urlEntityReferenceResolver.resolve(pdfURL, EntityType.ATTACHMENT));

        //  If the attachment reference name is the same as the given url, it means that the url directs to a file
        //  outside of XWiki instance and there is no need to check the user view right or delegated rights. We still
        //  send the attachment reference to be able to extract the name for it to be displayed in a tab in the case
        //  of multiple attachments.
        PDFFile pdfFile = new PDFFile();
        if (attachmentReference.getName().equals(pdfURL)) {
            pdfFile.setUrl(pdfURL);
            pdfFile.setAttachmentReference(attachmentReference);
            return pdfFile;
        } else if (hasViewRights(attachmentReference.getDocumentReference(), delegatedRights, pdfFile)) {
            DocumentReference docRef = attachmentReference.getDocumentReference();
            XWikiContext wikiContext = wikiContextProvider.get();
            XWikiDocument doc = wikiContext.getWiki().getDocument(docRef, wikiContext);
            // If the user has the rights to view the attachment parent document, but the attachment is not found, an
            // empty URL is returned.
            pdfFile.setAttachmentReference(attachmentReference);
            if (doc.getAttachment(attachmentReference.getName()) != null) {
                pdfFile.setUrl(pdfURL);
            }
            return pdfFile;
        }
        return pdfFile;
    }

    private PDFFile handleInternalAttachment(String pdfFileReference, String delegatedRights,
        String ownerDocumentReference) throws XWikiException
    {
        XWikiContext wikiContext = wikiContextProvider.get();
        XWikiDocument ownerDocument = getOwnerDocumentFromParameters(ownerDocumentReference, delegatedRights);
        XWikiAttachment attachment = ownerDocument.getAttachment(pdfFileReference);

        // If the attachment is not in the current or given document, the file parameter is used as a full reference.
        if (attachment == null) {
            AttachmentReference attachmentReference =
                new AttachmentReference(this.entityReferenceResolver.resolve(pdfFileReference, EntityType.ATTACHMENT));
            DocumentReference parentDocRef = new DocumentReference(attachmentReference.getParent());
            return getPDFFile(attachmentReference, parentDocRef, delegatedRights);
        } else {
            String url = ownerDocument.getAttachmentURL(attachment.getFilename(), wikiContext);
            return new PDFFile(attachment.getReference(), url);
        }
    }

    private PDFFile getPDFFile(AttachmentReference attachmentReference, DocumentReference parentDocRef,
        String delegatedRights) throws XWikiException
    {
        PDFFile pdfFile = new PDFFile();
        if (hasViewRights(parentDocRef, delegatedRights, pdfFile)) {
            XWikiContext wikiContext = this.wikiContextProvider.get();
            String attachName = attachmentReference.getName();
            XWikiDocument attachmentDocument = wikiContext.getWiki().getDocument(parentDocRef, wikiContext);
            XWikiAttachment attachment = attachmentDocument.getAttachment(attachName);
            // If the attachment does not exist, an empty URL is returned, alongside with the attachment reference.
            pdfFile.setAttachmentReference(attachmentReference);
            if (attachment != null) {
                String url = attachmentDocument.getAttachmentURL(attachName, wikiContext);
                pdfFile.setUrl(url);
            }
            return pdfFile;
        }
        return pdfFile;
    }

    private XWikiDocument getOwnerDocumentFromParameters(String ownerDocumentReference, String delegatedRights)
        throws XWikiException
    {
        // For backwards compatibility with the macros added in older versions, first it is checked if the file name
        // specified exists on the defined document or on the current one before considering the file parameter as the
        // full reference.
        XWikiContext wikiContext = wikiContextProvider.get();
        XWikiDocument attachmentDocument = wikiContext.getDoc();
        if (ownerDocumentReference != null && !ownerDocumentReference.isEmpty()) {
            DocumentReference givenDocumentReference =
                new DocumentReference(entityReferenceResolver.resolve(ownerDocumentReference, EntityType.DOCUMENT));
            if (hasViewRights(givenDocumentReference, delegatedRights, null)) {
                attachmentDocument = wikiContext.getWiki().getDocument(givenDocumentReference, wikiContext);
            }
        }
        return attachmentDocument;
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
