package org.alfresco.extension.pdftoolkit.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.text.SimpleDateFormat;

import javax.imageio.ImageIO;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.extension.pdftoolkit.constants.PDFToolkitConstants;
import org.alfresco.extension.pdftoolkit.model.PDFToolkitModel;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.ScriptLogger.SystemOut;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.template.FreeMarkerProcessor;
import org.alfresco.repo.template.TemplateNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.model.FileExistsException;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.extensions.surf.util.I18NUtil;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.Utilities;
import com.itextpdf.text.pdf.Barcode;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfDate;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignature;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;




import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.Barcode128;
import com.itextpdf.text.BaseColor;





public class PDFToolkitServiceImpl extends PDFToolkitConstants implements PDFToolkitService 
{
    private final static String MSGID_PAGE_NUMBERING_PATTERN_MULTIPLE="pdftoolkit.split-page-numbering-pattern-multiple";
    private final static String MSGID_PAGE_NUMBERING_PATTERN_SINGLE="pdftoolkit.split-page-numbering-pattern-single";
    // Dev 
    // Test /opt/formacao/fonts
    // Prod /opt/java/tomcat/fonts
    private final static String fonts = "/opt/java/tomcat/fonts/BookmanOldStyleRegular.ttf";

	private ServiceRegistry serviceRegistry;
    private NodeService ns;
    private ContentService cs;
    private FileFolderService ffs;
    private DictionaryService ds;
    private PersonService ps;
    private AuthenticationService as;
    
    private FreeMarkerProcessor freemarkerProcessor = new FreeMarkerProcessor();
    
    private static Log logger = LogFactory.getLog(PDFToolkitServiceImpl.class);
    
    // do we need to apply the encryption aspect when we encrypt?
    private boolean useEncryptionAspect = true;
    
    // do we need to apply the signature aspect when we sign?
    private boolean useSignatureAspect = true;

    // aspect for comment documents
    private boolean useFirstCommentAspect = true;
    private boolean useSecondCommentAspect = true;
    private boolean useThirdCommentAspect = true;
    private boolean useFifthCommentAspect = true;
    private boolean useSixthCommentAspect = true;
    
    // when we create a new document, do we actually create a new one, or copy the source?
    private boolean createNew = false;
    
    @Override
    public NodeRef appendPDF(NodeRef targetNodeRef, Map<String, Serializable> params)
    {
    	PDDocument pdf = null;
        PDDocument pdfTarget = null;
        InputStream is = null;
        InputStream tis = null;
        File tempDir = null;
        ContentWriter writer = null;
        NodeRef destinationNode = null;
        
        try
        {
        	NodeRef toAppend = (NodeRef)params.get(PARAM_TARGET_NODE);
        	Boolean inplace = Boolean.valueOf(String.valueOf(params.get(PARAM_INPLACE)));
        	ContentReader append = getReader(toAppend);
            is = append.getContentInputStream();
            
            ContentReader targetReader = getReader(targetNodeRef);
            tis = targetReader.getContentInputStream();
            
            String fileName = getFilename(params, targetNodeRef);
            
            // stream the document in
            pdf = PDDocument.load(is);
            pdfTarget = PDDocument.load(tis);
            
            // Append the PDFs
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.appendDocument(pdfTarget, pdf);
            merger.setDestinationFileName(fileName);
            merger.mergeDocuments();

            // build a temp dir name based on the ID of the noderef we are
            // importing
            File alfTempDir = TempFileProvider.getTempDir();
            tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
            tempDir.mkdir();
            
            pdfTarget.save(tempDir + "" + File.separatorChar + fileName);

            for (File file : tempDir.listFiles())
            {
                try
                {
                    if (file.isFile())
                    {
                        // Get a writer and prep it for putting it back into the repo
                        destinationNode = createDestinationNode(fileName, 
                        		(NodeRef)params.get(PARAM_DESTINATION_FOLDER), targetNodeRef, inplace);
                        writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);
                        
                        writer.setEncoding(targetReader.getEncoding()); // original
                                                                  // encoding
                        writer.setMimetype(FILE_MIMETYPE);

                        // Put it in the repo
                        writer.putContent(file);

                        // Clean up
                        file.delete();
                    }
                }
                catch (FileExistsException e)
                {
                    throw new AlfrescoRuntimeException("Failed to process file.", e);
                }
            }
        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }

        finally
        {
            if (pdf != null)
            {
                try
                {
                    pdf.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }
            }
            if (pdfTarget != null)
            {
                try
                {
                    pdfTarget.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }
            }
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }
            }

            if (tempDir != null)
            {
                tempDir.delete();
            }
        }
        
        return destinationNode;
    }
    
	@Override
	public NodeRef encryptPDF(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{
		PdfStamper stamp = null;
        File tempDir = null;
        ContentWriter writer = null;
        ContentReader targetReader = null;
        NodeRef destinationNode = null;
        
        try
        {
            // get the parameters
            String userPassword = (String)params.get(PARAM_USER_PASSWORD);
            String ownerPassword = (String)params.get(PARAM_OWNER_PASSWORD);
            Boolean inplace = Boolean.valueOf(String.valueOf(params.get(PARAM_INPLACE)));
            int permissions = buildPermissionMask(params);
            int encryptionType = Integer.parseInt((String)params.get(PARAM_ENCRYPTION_LEVEL));

            // if metadata is excluded, alter encryption type
            if ((Boolean)params.get(PARAM_EXCLUDE_METADATA))
            {
                encryptionType = encryptionType | PdfWriter.DO_NOT_ENCRYPT_METADATA;
            }

            // get temp file
            File alfTempDir = TempFileProvider.getTempDir();
            tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
            tempDir.mkdir();
            File file = new File(tempDir, ffs.getFileInfo(targetNodeRef).getName());

            // get the PDF input stream and create a reader for iText
            targetReader = getReader(targetNodeRef);
            PdfReader reader = new PdfReader(targetReader.getContentInputStream());
            stamp = new PdfStamper(reader, new FileOutputStream(file));

            // encrypt PDF
            stamp.setEncryption(userPassword.getBytes(Charset.forName("UTF-8")), ownerPassword.getBytes(Charset.forName("UTF-8")), permissions, encryptionType);
            stamp.close();

            String fileName = getFilename(params, targetNodeRef);
            
            // write out to destination
            destinationNode = createDestinationNode(fileName, 
            		(NodeRef)params.get(PARAM_DESTINATION_FOLDER), targetNodeRef, inplace);
            writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);

            writer.setEncoding(targetReader.getEncoding());
            writer.setMimetype(FILE_MIMETYPE);
            writer.putContent(file);
            file.delete();
            
            //if useAspect is true, store some additional info about the signature in the props
            if(useEncryptionAspect)
            {
            	ns.addAspect(destinationNode, PDFToolkitModel.ASPECT_ENCRYPTED, new HashMap<QName, Serializable>());
            	ns.setProperty(destinationNode, PDFToolkitModel.PROP_ENCRYPTIONDATE, new java.util.Date());
            	ns.setProperty(destinationNode, PDFToolkitModel.PROP_ENCRYPTEDBY, AuthenticationUtil.getRunAsUser());
            }
            
        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        catch (DocumentException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        finally
        {
            if (tempDir != null)
            {
                try
                {
                    tempDir.delete();
                }
                catch (Exception ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }

            if (stamp != null)
            {
                try
                {
                    stamp.close();
                }
                catch (Exception ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }
        }
        
        return destinationNode;
	}

	@Override 
	public NodeRef decryptPDF(NodeRef targetNodeRef, Map<String, Serializable> params)
	{
		PdfStamper stamp = null;
        File tempDir = null;
        ContentWriter writer = null;
        ContentReader targetReader = null;
        NodeRef destinationNode = null;
        
        try
        {
            // get the parameters
            String ownerPassword = (String)params.get(PARAM_OWNER_PASSWORD);
            Boolean inplace = Boolean.valueOf(String.valueOf(params.get(PARAM_INPLACE)));

            // get temp file
            File alfTempDir = TempFileProvider.getTempDir();
            tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
            tempDir.mkdir();
            File file = new File(tempDir, ffs.getFileInfo(targetNodeRef).getName());

            // get the PDF input stream and create a reader for iText
            targetReader = getReader(targetNodeRef);
            PdfReader reader = new PdfReader(targetReader.getContentInputStream(), ownerPassword.getBytes());
            stamp = new PdfStamper(reader, new FileOutputStream(file));
            stamp.close();

            String fileName = getFilename(params, targetNodeRef);
            
            // write out to destination
            destinationNode = createDestinationNode(fileName, 
            		(NodeRef)params.get(PARAM_DESTINATION_FOLDER), targetNodeRef, inplace);
            writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);

            writer.setEncoding(targetReader.getEncoding());
            writer.setMimetype(FILE_MIMETYPE);
            writer.putContent(file);
            file.delete();
            
            //if useAspect is true, store some additional info about the signature in the props
            if(useEncryptionAspect)
            {
            	ns.removeAspect(destinationNode, PDFToolkitModel.ASPECT_ENCRYPTED);
            }
        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        catch (DocumentException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        finally
        {
            if (tempDir != null)
            {
                try
                {
                    tempDir.delete();
                }
                catch (Exception ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }

            if (stamp != null)
            {
                try
                {
                    stamp.close();
                }
                catch (Exception ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }
        }
        
        return destinationNode;
	}
	
	@Override
	public NodeRef signPDF(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{
		NodeRef privateKey = (NodeRef)params.get(PARAM_PRIVATE_KEY);
		String location = (String)params.get(PARAM_LOCATION);
		String position = (String)params.get(PARAM_POSITION);
		String reason = (String)params.get(PARAM_REASON);
		String visibility = (String)params.get(PARAM_VISIBILITY);
		String keyPassword = (String)params.get(PARAM_KEY_PASSWORD);
		String keyType = (String)params.get(PARAM_KEY_TYPE);
		int height = getInteger(params.get(PARAM_HEIGHT));
		int width = getInteger(params.get(PARAM_WIDTH));
		int pageNumber = getInteger(params.get(PARAM_PAGE));
		
		// By default, append the signature as a new PDF revision to avoid
		// invalidating any signatures that might already exist on the doc
		boolean appendToExisting = true;
		if (params.get(PARAM_NEW_REVISION) != null) {
			appendToExisting = Boolean.valueOf(String.valueOf(params.get(PARAM_NEW_REVISION)));
		}
		
		// New keystore parameters
		String alias = (String)params.get(PARAM_ALIAS);
		String storePassword = (String)params.get(PARAM_STORE_PASSWORD);

		int locationX = getInteger(params.get(PARAM_LOCATION_X));
		int locationY = getInteger(params.get(PARAM_LOCATION_Y));

		Boolean inplace = Boolean.valueOf(String.valueOf(params.get(PARAM_INPLACE)));

		File tempDir = null;
		ContentWriter writer = null;
		KeyStore ks = null;

		NodeRef destinationNode = null;
		
		try
		{
			// get a keystore instance by
			if (keyType == null || keyType.equalsIgnoreCase(KEY_TYPE_DEFAULT))
			{
				ks = KeyStore.getInstance(KeyStore.getDefaultType());
			}
			else if (keyType.equalsIgnoreCase(KEY_TYPE_PKCS12))
			{
				ks = KeyStore.getInstance("pkcs12");
			}
			else
			{
				throw new AlfrescoRuntimeException("Unknown key type " + keyType + " specified");
			}

			// open the reader to the key and load it
			ContentReader keyReader = getReader(privateKey);
			ks.load(keyReader.getContentInputStream(), storePassword.toCharArray());

			// set alias
			// String alias = (String) ks.aliases().nextElement();

			PrivateKey key = (PrivateKey)ks.getKey(alias, keyPassword.toCharArray());
			Certificate[] chain = ks.getCertificateChain(alias);

			// open original pdf
			ContentReader pdfReader = getReader(targetNodeRef);
			PdfReader reader = new PdfReader(pdfReader.getContentInputStream());

			// If the page number is 0 because it couldn't be parsed or for
			// some other reason, set it to the first page, which is 1.
			// If the page number is negative, assume the intent is to "wrap".
			// For example, -1 would always be the last page.
			int numPages = reader.getNumberOfPages();
			if (pageNumber < 1 && pageNumber == 0) {
				pageNumber = 1; // use the first page
			} else {
				// page number is negative
				pageNumber = numPages + 1 + pageNumber;
				if (pageNumber <= 0) pageNumber = 1;
			}
			
			// if the page number specified is more than the num of pages,
			// use the last page
			if (pageNumber > numPages) {
				pageNumber = numPages;
			}
			
			// create temp dir to store file
			File alfTempDir = TempFileProvider.getTempDir();
			tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
			tempDir.mkdir();
			File file = new File(tempDir, ffs.getFileInfo(targetNodeRef).getName());

			FileOutputStream fout = new FileOutputStream(file);
			
			// When adding a second signature, append must be called on PdfStamper.createSignature
			// to avoid invalidating previous signatures
			PdfStamper stamp = null;
			if (appendToExisting) {
				stamp = PdfStamper.createSignature(reader, fout, '\0', tempDir, true);
			} else {
				stamp = PdfStamper.createSignature(reader, fout, '\0');
			}
			
			PdfSignatureAppearance sap = stamp.getSignatureAppearance();
			// set reason for signature and location of signer
			sap.setReason(reason);
			sap.setLocation(location);
						
			PdfSignature dic = new PdfSignature(
					PdfName.ADOBE_PPKLITE, PdfName.ADBE_PKCS7_DETACHED);
					dic.setReason(sap.getReason());
					dic.setLocation(sap.getLocation());
					dic.setContact(sap.getContact());
					dic.setDate(new PdfDate(sap.getSignDate()));
					
			//sap.setCrypto(key, chain, null, PdfSignatureAppearance.WINCER_SIGNED);
			
			if (visibility.equalsIgnoreCase(VISIBILITY_VISIBLE))
			{
				//create the signature rectangle using either the provided position or
				//the exact coordinates, if provided
				if(position != null && !position.trim().equalsIgnoreCase("") 
						&& !position.trim().equalsIgnoreCase(POSITION_MANUAL))
				{
					Rectangle pageRect = reader.getPageSizeWithRotation(pageNumber);
					sap.setVisibleSignature(positionSignature(position, pageRect, width, height), pageNumber, null);
				}
				else
				{
					sap.setVisibleSignature(new Rectangle(locationX, locationY, locationX + width, locationY - height), pageNumber, null);
				}
			}
			
			sap.setCryptoDictionary(dic);

			stamp.close();

			String fileName = getFilename(params, targetNodeRef);

			destinationNode = createDestinationNode(fileName, 
					(NodeRef)params.get(PARAM_DESTINATION_FOLDER), targetNodeRef, inplace);
			writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);

			writer.setEncoding(pdfReader.getEncoding());
			writer.setMimetype(FILE_MIMETYPE);
			writer.putContent(file);

			file.delete();

			//if useAspect is true, store some additional info about the signature in the props
			if(useSignatureAspect)
			{
				ns.addAspect(destinationNode, PDFToolkitModel.ASPECT_SIGNED, new HashMap<QName, Serializable>());
				ns.setProperty(destinationNode, PDFToolkitModel.PROP_REASON, reason);
				ns.setProperty(destinationNode, PDFToolkitModel.PROP_LOCATION, location);
				ns.setProperty(destinationNode, PDFToolkitModel.PROP_SIGNATUREDATE, new java.util.Date());
				ns.setProperty(destinationNode, PDFToolkitModel.PROP_SIGNEDBY, AuthenticationUtil.getRunAsUser());
			}

		}
		catch (IOException e)
		{
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
		catch (KeyStoreException e)
		{
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
		catch (ContentIOException e)
		{
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
		catch (CertificateException e)
		{
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
		catch (UnrecoverableKeyException e)
		{
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
		catch (DocumentException e)
		{
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
		finally
		{
			if (tempDir != null)
			{
				try
				{
					tempDir.delete();
				}
				catch (Exception ex)
				{
					throw new AlfrescoRuntimeException(ex.getMessage(), ex);
				}
			}
		}
		
		return destinationNode;
	}

	@Override
	public NodeRef watermarkPDF(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{

		NodeRef destinationNode = null;
        //String templateText = (String)params.get(PARAM_WATERMARK_TEXT);
		
        try
        {
        	ContentReader targetReader = getReader(targetNodeRef);
        	
            if (params.get(PARAM_WATERMARK_TYPE) != null
                     && params.get(PARAM_WATERMARK_TYPE).equals(TYPE_TEXT))
            {
                destinationNode = this.textAction(params, targetNodeRef, targetReader);			
            }

            
        }
        
        catch (AlfrescoRuntimeException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        
        return destinationNode;
	}

	@Override
	public NodeRef splitPDF(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{
        PDDocument pdf = null;
        InputStream is = null;
        File tempDir = null;
        ContentWriter writer = null;
        NodeRef destinationFolder;
        
        try
        {
        	destinationFolder = (NodeRef)params.get(PARAM_DESTINATION_FOLDER);
        	
        	ContentReader targetReader = getReader(targetNodeRef);
        	
            // Get the split frequency
            int splitFrequency = 0;

            String splitFrequencyString = params.get(PARAM_SPLIT_FREQUENCY).toString();
            if (!splitFrequencyString.equals(""))
            {
                splitFrequency = Integer.valueOf(splitFrequencyString);
            }

            // Get contentReader inputStream
            is = targetReader.getContentInputStream();
            // stream the document in
            pdf = PDDocument.load(is);
            // split the PDF and put the pages in a list
            Splitter splitter = new Splitter();
            // if the default split is not every page, then set it to the right
            // frequency
            if (splitFrequency > 0)
            {
                splitter.setSplitAtPage(splitFrequency);
            }
            // Split the pages
            List<PDDocument> pdfs = splitter.split(pdf);

            // Lets get reading to walk the list
            Iterator<PDDocument> it = pdfs.iterator();

            // Start page split numbering at
            int page = 1;
            int endPage = 0;

            // build a temp dir name based on the ID of the noderef we are
            // importing
            File alfTempDir = TempFileProvider.getTempDir();
            tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
            tempDir.mkdir();

            while (it.hasNext())
            {
                // Get the split document and save it into the temp dir with new
                // name
                PDDocument splitpdf = (PDDocument)it.next();

                int pagesInPDF = splitpdf.getNumberOfPages();

                if (splitFrequency > 0)
                {
                    endPage = endPage + pagesInPDF;
                }

                // put together the name and save the PDF
                String fileNameSansExt = getFilenameSansExt(targetNodeRef, FILE_EXTENSION);
                splitpdf.save(tempDir + "" + File.separatorChar + fileNameSansExt + formatPageNumbering(page, endPage) + FILE_EXTENSION);

                // increment page count
                if (splitFrequency > 0)
                {
                    page = (page++) + pagesInPDF;
                }
                else
                {
                    page++;
                }

                try
                {
                    splitpdf.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }

            }

            for (File file : tempDir.listFiles())
            {
                try
                {
                    if (file.isFile())
                    {

                        // Get a writer and prep it for putting it back into the
                        // repo
                        NodeRef destinationNode = createDestinationNode(file.getName(), 
                        		destinationFolder, targetNodeRef, false);
                        writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);
                        
                        writer.setEncoding(targetReader.getEncoding()); // original
                                                                  // encoding
                        writer.setMimetype(FILE_MIMETYPE);

                        // Put it in the repo
                        writer.putContent(file);

                        // Clean up
                        file.delete();
                    }
                }
                catch (FileExistsException e)
                {
                    throw new AlfrescoRuntimeException("Failed to process file.", e);
                }
            }
        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }

        finally
        {
            if (pdf != null)
            {
                try
                {
                    pdf.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }
            }
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }
            }

            if (tempDir != null)
            {
                tempDir.delete();
            }
        }
        
        return destinationFolder;
	}

	@Override
	public NodeRef splitPDFAtPage(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{
		PDDocument pdf = null;
		InputStream is = null;
		File tempDir = null;
		ContentWriter writer = null;
		NodeRef destinationFolder;

		try
		{
			destinationFolder = (NodeRef)params.get(PARAM_DESTINATION_FOLDER);
			ContentReader targetReader = getReader(targetNodeRef);

			// Get the split frequency
			int splitPageNumber = 0;

			String splitPage = params.get(PARAM_PAGE).toString();
			if (!splitPage.equals(""))
			{
				try
				{
					splitPageNumber = Integer.valueOf(splitPage);
				}
				catch (NumberFormatException e)
				{
					throw new AlfrescoRuntimeException(e.getMessage(), e);
				}
			}

			// Get contentReader inputStream
			is = targetReader.getContentInputStream();
			// stream the document in
			pdf = PDDocument.load(is);
			// split the PDF and put the pages in a list
			Splitter splitter = new Splitter();
			// Need to adjust the input value to get the split at the right page
			splitter.setSplitAtPage(splitPageNumber - 1);

			// Split the pages
			List<PDDocument> pdfs = splitter.split(pdf);

			// Start page split numbering at
			int page = 1;

			// build a temp dir, name based on the ID of the noderef we are
			// importing
			File alfTempDir = TempFileProvider.getTempDir();
			tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
			tempDir.mkdir();

			// FLAG: This is ugly.....get the first PDF.
			PDDocument firstPDF = (PDDocument)pdfs.remove(0);

			int pagesInFirstPDF = firstPDF.getNumberOfPages();

			int lastPage = 0;

			if (pagesInFirstPDF > 1)
			{
				lastPage = pagesInFirstPDF;
			}

			String fileNameSansExt = getFilenameSansExt(targetNodeRef, FILE_EXTENSION);
			firstPDF.save(tempDir + "" + File.separatorChar + fileNameSansExt + formatPageNumbering(page, lastPage) + FILE_EXTENSION);

			try
			{
				firstPDF.close();
			}
			catch (IOException e)
			{
				throw new AlfrescoRuntimeException(e.getMessage(), e);
			}

			// FLAG: Like I said: "_UGLY_" ..... and it gets worse
			PDDocument secondPDF = null;

			Iterator<PDDocument> its = pdfs.iterator();

			int pagesInSecondPDF = 0;

			while (its.hasNext())
			{
				if (secondPDF != null)
				{
					// Get the split document and save it into the temp dir with
					// new name
					PDDocument splitpdf = (PDDocument)its.next();

					int pagesInThisPDF = splitpdf.getNumberOfPages();
					pagesInSecondPDF = pagesInSecondPDF + pagesInThisPDF;

					PDFMergerUtility merger = new PDFMergerUtility();
					merger.appendDocument(secondPDF, splitpdf);
					merger.mergeDocuments();

					try
					{
						splitpdf.close();
					}
					catch (IOException e)
					{
						throw new AlfrescoRuntimeException(e.getMessage(), e);
					}
				}
				else
				{
					secondPDF = (PDDocument)its.next();
					pagesInSecondPDF = secondPDF.getNumberOfPages();
				}
			}

			if (pagesInSecondPDF > 1)
			{
				lastPage = pagesInSecondPDF + pagesInFirstPDF;
			}
			else
			{
				lastPage = 0;
			}

			// This is where we should save the appended PDF
			// put together the name and save the PDF
			secondPDF.save(tempDir + "" + File.separatorChar + fileNameSansExt + formatPageNumbering(splitPageNumber, lastPage) + FILE_EXTENSION);

			for (File file : tempDir.listFiles())
			{
				try
				{
					if (file.isFile())
					{
						// Get a writer and prep it for putting it back into the
						// repo
						NodeRef destinationNode = createDestinationNode(file.getName(), 
								destinationFolder, targetNodeRef, false);
						writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);

						writer.setEncoding(targetReader.getEncoding()); // original
						// encoding
						writer.setMimetype(FILE_MIMETYPE);

						// Put it in the repo
						writer.putContent(file);

						// Clean up
						file.delete();
					}
				}
				catch (FileExistsException e)
				{
					throw new AlfrescoRuntimeException("Failed to process file.", e);
				}
			}
		}
		catch (IOException e)
		{
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}

		finally
		{
			if (pdf != null)
			{
				try
				{
					pdf.close();
				}
				catch (IOException e)
				{
					throw new AlfrescoRuntimeException(e.getMessage(), e);
				}
			}
			if (is != null)
			{
				try
				{
					is.close();
				}
				catch (IOException e)
				{
					throw new AlfrescoRuntimeException(e.getMessage(), e);
				}
			}

			if (tempDir != null)
			{
				tempDir.delete();
			}
		}
		return destinationFolder;
	}

	@Override
	public NodeRef insertPDF(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{
		PDDocument pdf = null;
        PDDocument insertContentPDF = null;
        InputStream is = null;
        InputStream cis = null;
        File tempDir = null;
        ContentWriter writer = null;
        NodeRef destinationNode = null;
        
        try
        {

        	ContentReader targetReader = getReader(targetNodeRef);
        	ContentReader insertReader = getReader((NodeRef)params.get(PARAM_INSERT_CONTENT));
            int insertAt = Integer.valueOf((String)params.get(PARAM_PAGE)).intValue();
            Boolean inplace = Boolean.valueOf(String.valueOf(params.get(PARAM_INPLACE)));
            
            // Get contentReader inputStream
            is = targetReader.getContentInputStream();
            // Get insertContentReader inputStream
            cis = insertReader.getContentInputStream();
            // stream the target document in
            pdf = PDDocument.load(is);
            // stream the insert content document in
            insertContentPDF = PDDocument.load(cis);

            // split the PDF and put the pages in a list
            Splitter splitter = new Splitter();

            // Split the pages
            List<PDDocument> pdfs = splitter.split(pdf);

            // Build the output PDF
            PDFMergerUtility merger = new PDFMergerUtility();
            
            PDDocument newDocument = new PDDocument();
            
            for (int i = 0; i < pdfs.size(); i++) {
            	
            	if (i == insertAt -1) {
            		merger.appendDocument(newDocument, insertContentPDF);
            	}
            	
            	merger.appendDocument(newDocument, (PDDocument)pdfs.get(i));
            }
            
            merger.setDestinationFileName(params.get(PARAM_DESTINATION_NAME).toString());
            merger.mergeDocuments();

            // build a temp dir, name based on the ID of the noderef we are
            // importing
            File alfTempDir = TempFileProvider.getTempDir();
            tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
            tempDir.mkdir();

            String fileName = params.get(PARAM_DESTINATION_NAME).toString();
            
            PDDocument completePDF = newDocument;

            completePDF.save(tempDir + "" + File.separatorChar + fileName + FILE_EXTENSION);

            try
            {
                completePDF.close();
                newDocument.close();
            }
            catch (IOException e)
            {
                throw new AlfrescoRuntimeException(e.getMessage(), e);
            }

            for (File file : tempDir.listFiles())
            {
                try
                {
                    if (file.isFile())
                    {

                        // Get a writer and prep it for putting it back into the
                        // repo
                        destinationNode = createDestinationNode(file.getName(), 
                        		(NodeRef)params.get(PARAM_DESTINATION_FOLDER), targetNodeRef, inplace);
                        writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);
                        
                        writer.setEncoding(targetReader.getEncoding()); // original
                        // encoding
                        writer.setMimetype(FILE_MIMETYPE);

                        // Put it in the repo
                        writer.putContent(file);

                        // Clean up
                        file.delete();
                    }
                }
                catch (FileExistsException e)
                {
                    throw new AlfrescoRuntimeException("Failed to process file.", e);
                }
            }
        }
        // TODO add better handling
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }

        finally
        {
            if (pdf != null)
            {
                try
                {
                    pdf.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }
            }
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }
            }

            if (tempDir != null)
            {
                tempDir.delete();
            }
        }
        
        return destinationNode;
    }

	@Override
	public NodeRef deletePagesFromPDF(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{
		String pages = String.valueOf(params.get(PARAM_PAGE));
		return subsetPDFDocument(targetNodeRef, params, pages);
	}

    

	@Override
	public NodeRef extractPagesFromPDF(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{
		String pages = String.valueOf(params.get(PARAM_PAGE));
		return subsetPDFDocument(targetNodeRef, params, pages);
	}

 
	@Override
	public NodeRef rotatePDF(NodeRef targetNodeRef, Map<String, Serializable> params) 
	{
		InputStream is = null;
        File tempDir = null;
        ContentWriter writer = null;
        PdfReader pdfReader = null;
        NodeRef destinationNode = null;

        Map<QName, Serializable> props = serviceRegistry.getNodeService().getProperties(destinationNode);
        String qrCodeString = new String();
		for (Map.Entry<QName,Serializable> entry : props.entrySet()){ 
            qrCodeString += entry.getKey().getLocalName()+" : "+entry.getValue()+"\n";
		}
        
        try
        {
        	ContentReader targetReader = getReader(targetNodeRef);
            is = targetReader.getContentInputStream();

            File alfTempDir = TempFileProvider.getTempDir();
            tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
            tempDir.mkdir();
            
            Boolean inplace = Boolean.valueOf(String.valueOf(params.get(PARAM_INPLACE)));
            String pages = String.valueOf(params.get(PARAM_PAGE));
         
            
            String fileName = getFilename(params, targetNodeRef);
            
            File file = new File(tempDir, ffs.getFileInfo(targetNodeRef).getName());

            pdfReader = new PdfReader(is);
            PdfStamper stamp = new PdfStamper(pdfReader, new FileOutputStream(file));
            
			

            int numPages = pdfReader.getNumberOfPages();

            for (int i = 1; i <= numPages; i++) 
            {
                // only apply barcode ...
                if (checkPage(pages, i, numPages))
                {
                    PdfContentByte over = stamp.getOverContent(numPages);
			        BarcodeQRCode barcodeQRCode = new BarcodeQRCode(qrCodeString, 1, 1, null);
            		Image qrcodeImage = barcodeQRCode.getImage();
			        qrcodeImage.setAbsolutePosition(480,70);
                    over.addImage(qrcodeImage);
                    System.out.println("qrCodeString = " + qrCodeString);

			        
                }
            }
           
            stamp.close();
            pdfReader.close();

            destinationNode = createDestinationNode(fileName,(NodeRef)params.get(PARAM_DESTINATION_FOLDER), targetNodeRef, inplace);
            writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);
            writer.setEncoding(targetReader.getEncoding());
            writer.setMimetype(FILE_MIMETYPE);

            // Put it in the repository
            writer.putContent(file);

            // Clean up
            file.delete();

        }
        catch (ContentIOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        } 
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        } 
        catch (DocumentException e) 
        {
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
        catch (Exception e)
        {
        	throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        finally
        {
            if (pdfReader != null)
            {
            	pdfReader.close();
            }
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                    throw new AlfrescoRuntimeException(e.getMessage(), e);
                }
            }

            if (tempDir != null)
            {
                tempDir.delete();
            }
        }
        return destinationNode;
	}

    private NodeRef subsetPDFDocument(NodeRef targetNodeRef, Map<String, Serializable> params, String pages) 
	{
		InputStream is = null;
	    File tempDir = null;
	    ContentWriter writer = null;
	    PdfReader pdfReader = null;
	    NodeRef destinationNode = null;
        
        
        Map<QName, Serializable> props = serviceRegistry.getNodeService().getProperties(targetNodeRef);
        String qrCodeString = new String();
        
		for (Map.Entry<QName,Serializable> entry : props.entrySet()){ 
            qrCodeString += entry.getKey().getLocalName()+" : "+entry.getValue()+"\n";
		}
	    
	    try
	    {
	    	ContentReader targetReader = getReader(targetNodeRef);
	        is = targetReader.getContentInputStream();   

            File alfTempDir = TempFileProvider.getTempDir();
	        tempDir = new File(alfTempDir.getPath() + File.separatorChar + targetNodeRef.getId());
	        tempDir.mkdir();
            Boolean inplace = Boolean.valueOf(String.valueOf(params.get(PARAM_INPLACE)));
    
            String fileName = getFilename(params, targetNodeRef);
            File file = getTempFile(targetNodeRef);

	        pdfReader = new PdfReader(is);
            PdfStamper stamp = new PdfStamper(pdfReader, new FileOutputStream(file));
	               
            
            int numPages = pdfReader.getNumberOfPages();

            for (int i = 1; i <= numPages; i++) 
            {
                // only apply barcode ...
                if (checkPage(pages, i, numPages))
                {
                    PdfContentByte over = stamp.getOverContent(numPages);
			        BarcodeQRCode barcodeQRCode = new BarcodeQRCode(qrCodeString, 1, 1, null);
            		Image qrcodeImage = barcodeQRCode.getImage();
			        qrcodeImage.setAbsolutePosition(480,70);
                    over.addImage(qrcodeImage);
                    stamp.close();
                    pdfReader.close();
                    System.out.println("qrCodeString = " + qrCodeString);
			        
                }
            }
	
           
            destinationNode = createDestinationNode(fileName, 
            (NodeRef)params.get(PARAM_DESTINATION_FOLDER), targetNodeRef, inplace);            
            writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);
            writer.setEncoding(targetReader.getEncoding());
            writer.setMimetype(FILE_MIMETYPE);

            // Put it in the repository
            writer.putContent(file);

            // Clean up
            file.delete();

            
	
	    }
        catch (ContentIOException e) {

            e.printStackTrace();
        }
	    catch (IOException e)
	    {
	        throw new AlfrescoRuntimeException(e.getMessage(), e);
	    } 
	    catch (DocumentException e) 
	    {
			throw new AlfrescoRuntimeException(e.getMessage(), e);
		}
	    catch (Exception e)
	    {
	    	throw new AlfrescoRuntimeException(e.getMessage(), e);
	    }
	    finally
	    {
	        if (pdfReader != null)
	        {
	        	pdfReader.close();
	        }
	        if (is != null)
	        {
	            try
	            {
	                is.close();
	            }
	            catch (IOException e)
	            {
	                throw new AlfrescoRuntimeException(e.getMessage(), e);
	            }
	        }
	
	        if (tempDir != null)
	        {
	            tempDir.delete();
	        }
	    }
	    return destinationNode;
	}

	
	private ContentReader getReader(NodeRef nodeRef)
    {
		// first, make sure the node exists
		if (ns.exists(nodeRef) == false)
        {
            // node doesn't exist - can't do anything
            throw new AlfrescoRuntimeException("NodeRef: " + nodeRef + " does not exist");
        }
		
        // Next check that the node is a sub-type of content
        QName typeQName = ns.getType(nodeRef);
        if (ds.isSubClass(typeQName, ContentModel.TYPE_CONTENT) == false)
        {
            // it is not content, so can't transform
            throw new AlfrescoRuntimeException("The selected node is not a content node");
        }

        // Get the content reader.  If it is null, can't do anything here
        ContentReader contentReader = cs.getReader(nodeRef, ContentModel.PROP_CONTENT);

        if(contentReader == null)
        {
        	throw new AlfrescoRuntimeException("The content reader for NodeRef: " + nodeRef + "is null");
        }
        
        return contentReader;
    }

    /**
     * @param ruleAction
     * @param filename
     * @return
     */
    private NodeRef createDestinationNode(String filename, NodeRef destinationParent, NodeRef target, boolean inplace)
    {

    	NodeRef destinationNode;
    	
    	// if inplace mode is turned on, the destination for the modified content
    	// is the original node
    	if(inplace)
    	{
    		return target;
    	}
    	
    	if(createNew)
    	{
	    	//create a file in the right location
	        FileInfo fileInfo = ffs.create(destinationParent, filename, ContentModel.TYPE_CONTENT);
	        destinationNode = fileInfo.getNodeRef();
    	}
    	else
    	{
    		try 
    		{
	    		FileInfo fileInfo = ffs.copy(target, destinationParent, filename);
	    		destinationNode = fileInfo.getNodeRef();
    		}
    		catch(FileNotFoundException fnf)
    		{
    			throw new AlfrescoRuntimeException(fnf.getMessage(), fnf);
    		}
    	}

        return destinationNode;
    }
    
    private int getInteger(Serializable val)
    {
    	if(val == null)
    	{ 
    		return 0;
    	}
    	try
    	{
    		return Integer.parseInt(val.toString());
    	}
    	catch(NumberFormatException nfe)
    	{
    		return 0;
    	}
    }
    
    private File getTempFile(NodeRef nodeRef)
    {
    	File alfTempDir = TempFileProvider.getTempDir();
        File toolkitTempDir = new File(alfTempDir.getPath() + File.separatorChar + nodeRef.getId());
        toolkitTempDir.mkdir();
        File file = new File(toolkitTempDir, ffs.getFileInfo(nodeRef).getName());
        
        return file;
    }
    
    private String getFilename(Map<String, Serializable> params, NodeRef targetNodeRef)
    {
    	Serializable providedName = params.get(PARAM_DESTINATION_NAME);
        String fileName = null;
        if(providedName != null)
        {
        	fileName = String.valueOf(providedName);
        	if(!fileName.endsWith(FILE_EXTENSION))
        	{
        		fileName = fileName + FILE_EXTENSION;
        	}
        }
        else
        {
        	fileName = String.valueOf(ns.getProperty(targetNodeRef, ContentModel.PROP_NAME));
        }
        return fileName;
    }
    
    /**
	 * Parses the list of pages or page ranges to delete and returns a list of page numbers 
	 * 
	 * @param list
	 * @return
	 */
	private List<Integer> parsePageList(String list)
	{
		List<Integer> pages = new ArrayList<Integer>();
		String[] tokens = list.split(",");
		for(String token : tokens)
		{
			//parse each, if one is not an int, log it but keep going
			try 
			{
				pages.add(Integer.parseInt(token));
			}
			catch(NumberFormatException nfe)
			{
				logger.warn("Page list contains non-numeric values");
			}
		}
		return pages;
	}
	
    /**
     * Build the permissions mask for iText
     * 
     * @param options
     * @return
     */
    private int buildPermissionMask(Map<String, Serializable> options)
    {
        int permissions = 0;

        if ((Boolean)options.get(PARAM_ALLOW_PRINT))
        {
            permissions = permissions | PdfWriter.ALLOW_PRINTING;
        }
        if ((Boolean)options.get(PARAM_ALLOW_COPY))
        {
            permissions = permissions | PdfWriter.ALLOW_COPY;
        }
        if ((Boolean)options.get(PARAM_ALLOW_CONTENT_MODIFICATION))
        {
            permissions = permissions | PdfWriter.ALLOW_MODIFY_CONTENTS;
        }
        if ((Boolean)options.get(PARAM_ALLOW_ANNOTATION_MODIFICATION))
        {
            permissions = permissions | PdfWriter.ALLOW_MODIFY_ANNOTATIONS;
        }
        if ((Boolean)options.get(PARAM_ALLOW_SCREEN_READER))
        {
            permissions = permissions | PdfWriter.ALLOW_SCREENREADERS;
        }
        if ((Boolean)options.get(PARAM_ALLOW_DEGRADED_PRINT))
        {
            permissions = permissions | PdfWriter.ALLOW_DEGRADED_PRINTING;
        }
        if ((Boolean)options.get(PARAM_ALLOW_ASSEMBLY))
        {
            permissions = permissions | PdfWriter.ALLOW_ASSEMBLY;
        }
        if ((Boolean)options.get(PARAM_ALLOW_FORM_FILL))
        {
            permissions = permissions | PdfWriter.ALLOW_FILL_IN;
        }

        return permissions;
    }
    
    /**
     * Create a rectangle for the visible signature using the selected position and signature size
     * 
     * @param position
     * @param width
     * @param height
     * @return
     */
    private Rectangle positionSignature(String position, Rectangle pageRect, int width, int height)
    {

    	float pageHeight = pageRect.getHeight();
    	float pageWidth = pageRect.getWidth();
    	
    	Rectangle r = null;
    	
    	if (position.equals(POSITION_BOTTOMLEFT))
    	{
    		r = new Rectangle(0, height, width, 0);
    	}
    	else if (position.equals(POSITION_BOTTOMRIGHT))
    	{
    		r = new Rectangle(pageWidth - width, height, pageWidth, 0);
    	}
    	else if (position.equals(POSITION_TOPLEFT))
    	{
    		r = new Rectangle(0, pageHeight, width, pageHeight - height);
    	}
    	else if (position.equals(POSITION_TOPRIGHT))
    	{
    		r = new Rectangle(pageWidth - width, pageHeight, pageWidth, pageHeight - height);
    	}
    	else if (position.equals(POSITION_CENTER))
    	{
    		r = new Rectangle((pageWidth / 2) - (width / 2), (pageHeight / 2) - (height / 2),
    				(pageWidth / 2) + (width / 2), (pageHeight / 2) + (height / 2));
    	}

    	return r;
    }
    
    /**
     * @param fileName
     * @param extension
     * @return
     */
    private String removeExtension(String fileName, String extension)
    {
        // Does the file have the extension?
        if (fileName != null && fileName.contains(extension))
        {
            // Where does the extension start?
            int extensionStartsAt = fileName.indexOf(extension);
            // Get the Filename sans the extension
            return fileName.substring(0, extensionStartsAt);
        }

        return fileName;
    }

    private String getFilename(NodeRef targetNodeRef)
    {
        FileInfo fileInfo = ffs.getFileInfo(targetNodeRef);
        String filename = fileInfo.getName();

        return filename;
    }

    private String getFilenameSansExt(NodeRef targetNodeRef, String extension)
    {
        String filenameSansExt;
        filenameSansExt = removeExtension(getFilename(targetNodeRef), extension);

        return filenameSansExt;
    }
    
   
    /**
     * Applies a text watermark (current date, user name, etc, depending on
     * options)
     * 
     * @param reader
     * @param writer
     * @param options
     */
    private NodeRef textAction(Map<String, Serializable> options, NodeRef targetNodeRef, ContentReader actionedUponContentReader)
    {

        PdfStamper stamp = null;
        File tempDir = null;
        ContentWriter writer = null;
        String watermarkText;
        StringTokenizer st;
        Vector<String> tokens = new Vector<String>();
        NodeRef destinationNode = null;
        
        final String currentUser = AuthenticationUtil.getRunAsUser();

        final SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy");


        
        
        try
        {
            File file = getTempFile(targetNodeRef);

            // get the PDF input stream and create a reader for iText
            PdfReader reader = new PdfReader(actionedUponContentReader.getContentInputStream());
            stamp = new PdfStamper(reader, new FileOutputStream(file));
            PdfContentByte pcb;

            // get the PDF pages and position
            String pages = (String)options.get(PARAM_PAGE);
            String position = (String)options.get(PARAM_POSITION);
            int locationX = getInteger(options.get(PARAM_LOCATION_X));
            int locationY = getInteger(options.get(PARAM_LOCATION_Y));
            Boolean inplace = Boolean.valueOf(String.valueOf(options.get(PARAM_INPLACE)));
            // pdf-toolkit-repo/src/main/resources/fonts/BookmanOldStyleRegular.ttf


            // create the base font for the text stamp
            //BaseFont bf = BaseFont.createFont((String)options.get(PARAM_WATERMARK_FONT), BaseFont.CP1250, BaseFont.EMBEDDED);
            
            BaseFont bf = BaseFont.createFont(fonts, BaseFont.WINANSI, BaseFont.EMBEDDED);
            

            // get watermark text and process template with model
            String templateText = (String)options.get(PARAM_WATERMARK_TEXT);
                       
            Map<String, Object> model = buildWatermarkTemplateModel(targetNodeRef);
            StringWriter watermarkWriter = new StringWriter();
           
            freemarkerProcessor.processString( templateText, model, watermarkWriter);
            watermarkText =   watermarkWriter.getBuffer().toString() ;
            String lineSeparator = System.lineSeparator();

            if(watermarkText.length() > 60){   
                String par1 = watermarkText.substring(0, 60);
                String par2 = lineSeparator + watermarkText.substring(60, watermarkText.length());
                String par3 ="";
                
                
                if(par2.length() > par2.length()/2){
                    par3 = lineSeparator + par2.substring(par2.length()/2, par2.length());
                }

                watermarkText = par1 + par2 + " "+ "\r\n" + "Comentado Por:" + "\r\n"
                + currentUser + " aos " + df.format(new Date()) + "               " ;                
            }

            // tokenize watermark text to support multiple lines and copy tokens
            // to vector for re-use
            st = new StringTokenizer(watermarkText, "\r\n", false);
            while (st.hasMoreTokens())
            {
                tokens.add(st.nextToken());
            }

                     // stamp each page
                    int numpages = reader.getNumberOfPages();
                    for (int i = 1; i <= numpages; i++)
                    {
                        Rectangle r = reader.getPageSizeWithRotation(i);

                        // if this is an under-text stamp, use getUnderContent.
                        // if this is an over-text stamp, use getOverContent.
                        // if (depth.equals(DEPTH_OVER))
                        // {
                            // remove condition DEPH_OVER
                        pcb = stamp.getOverContent(i);
                       
                        // set the font and size
                        float size = 10;
                        pcb.setFontAndSize(bf, size);

                        if (checkPage(pages, i, numpages))
                        { 
                            if((!ns.hasAspect(targetNodeRef, PDFToolkitModel.ASPECT_COMMENT) && position.equals("topright") )
                             || ( !ns.hasAspect(targetNodeRef, PDFToolkitModel.ASPECT_COMMENT2) && position.equals("topleft") )
                             || ( !ns.hasAspect(targetNodeRef, PDFToolkitModel.ASPECT_COMMENT3) && position.equals("topleft2") )
                             || ( !ns.hasAspect(targetNodeRef, PDFToolkitModel.ASPECT_COMMENT4) && position.equals("bottomright") )
                             || ( !ns.hasAspect(targetNodeRef, PDFToolkitModel.ASPECT_COMMENT5) && position.equals("bottomleft") )
                             ){
                                writeAlignedText(pcb, r, tokens, size, position, locationX, locationY); 
                            }else{
                               System.out.println("Position = topright Already Commented Try again");
                               throw new AlfrescoRuntimeException("Position = topright Already Commented Try again ");
                            }
                            
                        }
                    }
                    
                 

            
            
            stamp.close();

            String fileName = getFilename(options, targetNodeRef);
            
            // Get a writer and prep it for putting it back into the repo
            //can't use BasePDFActionExecuter.getWriter here need the nodeRef of the destination
            destinationNode = createDestinationNode(fileName, 
            		(NodeRef)options.get(PARAM_DESTINATION_FOLDER), targetNodeRef, inplace);
            writer = cs.getWriter(destinationNode, ContentModel.PROP_CONTENT, true);
            writer.setEncoding(actionedUponContentReader.getEncoding());
            writer.setMimetype(FILE_MIMETYPE);

            // Put it in the repo
            writer.putContent(file);

           

            // delete the temp file
            file.delete();

            //set aspect for first comment and second comment
            if(useFirstCommentAspect && position.equals("topright") )
            {
                ns.addAspect(destinationNode, PDFToolkitModel.ASPECT_COMMENT, new HashMap<QName, Serializable>());
            }
            
            if(useSecondCommentAspect && position.equals("topleft")){
                ns.addAspect(destinationNode, PDFToolkitModel.ASPECT_COMMENT2, new HashMap<QName, Serializable>());
                
            }

            if(useThirdCommentAspect && position.equals("topleft2")){
                ns.addAspect(destinationNode, PDFToolkitModel.ASPECT_COMMENT3, new HashMap<QName, Serializable>());
                
            }

            if(useFifthCommentAspect && position.equals("bottomright")){
                ns.addAspect(destinationNode, PDFToolkitModel.ASPECT_COMMENT4, new HashMap<QName, Serializable>());
                
            }

            if(useSixthCommentAspect && position.equals("bottomleft")){
                ns.addAspect(destinationNode, PDFToolkitModel.ASPECT_COMMENT5, new HashMap<QName, Serializable>());
                
            }
        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        catch (DocumentException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        finally
        {
            if (tempDir != null)
            {
                try
                {
                    tempDir.delete();
                }
                catch (Exception ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }

            if (stamp != null)
            {
                try
                {
                    stamp.close();
                }
                catch (Exception ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }
        }
        
        return destinationNode;
    }
 /**
     * Writes text watermark to one of the 5 preconfigured locations
     * 
     * @param pcb
     * @param r
     * @param tokens
     * @param size
     * @param position
     */
    private void writeAlignedText(PdfContentByte pcb, Rectangle r, Vector<String> tokens, float size, 
    		String position, int locationX, int locationY)
    {
        // get the dimensions of our 'rectangle' for text
        float height = size * tokens.size();
        float width = 0;
        float centerX = 0, startY = 0;
        for (int i = 0; i < tokens.size(); i++)
        {
            if (pcb.getEffectiveStringWidth(tokens.get(i), false) > width)
            {
                width = pcb.getEffectiveStringWidth(tokens.get(i), false);
            }
        }

        // now that we have the width and height, we can calculate the center
        // position for
        // the rectangle that will contain our text.
        if (position.equals(POSITION_BOTTOMLEFT))
        {
        	centerX = width / 2 + PAD;
            startY = 6 * PAD + height;
        }
        else if (position.equals(POSITION_BOTTOMRIGHT))
        {
            centerX = r.getWidth() - (width + 17) + PAD;
            startY = 6 * PAD + height;
        }
        // use second comment aspect
        else if (position.equals(POSITION_TOPLEFT))
        {
            
            centerX = r.getWidth() - (width + 17) + PAD;
            startY = r.getHeight() - (PAD * 8);
        }
        else if (position.equals(POSITION_TOPLEFT2))
        {
            centerX = width / 2 + PAD;
            startY = r.getHeight() - (PAD * 8);
        }
        //use first comment aspect
        else if (position.equals(POSITION_TOPRIGHT))
        {
            // if(tokens.size() > 0){
                
            // }
            centerX = r.getWidth() - (width + 17) + PAD;
            startY = r.getHeight() - (PAD * 2);
        }
        else if (position.equals(POSITION_CENTER))
        {
            centerX = r.getWidth() / 2;
            startY = (r.getHeight() / 2) + (height / 2);
        }
        else if (position.equals(POSITION_MANUAL))
        {
        	centerX = r.getWidth() / 2 - locationX;
        	startY = locationY;
        }

        // apply text to PDF

         
        pcb.beginText();

        // Por continuar ...
        // PdfTemplate text = pcb.createTemplate(120, 60);
        // ColumnText column = new ColumnText(text);
        // column.setSimpleColumn(new Rectangle(120,60));
        //column.addElement(new Paragraph(tokens.get(tokens.size())));



        for (int t = 0; t < tokens.size(); t++)
        {    
            pcb.showTextAligned(PdfContentByte.ALIGN_LEFT, tokens.get(t), centerX, startY - (size * t), 0);
        }

        pcb.endText();

    }

    /**
     * Builds a freemarker model which supports a subset of the default model.
     * 
     * @param ref
     * @return
     */
    private Map<String, Object> buildWatermarkTemplateModel(NodeRef ref)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        NodeRef person = ps.getPerson(as.getCurrentUserName());
        model.put("person", new TemplateNode(person, serviceRegistry, null));
        NodeRef homespace = (NodeRef)ns.getProperty(person, ContentModel.PROP_HOMEFOLDER);
        model.put("userhome", new TemplateNode(homespace, serviceRegistry, null));
        model.put("document", new TemplateNode(ref, serviceRegistry, null));
        NodeRef parent = ns.getPrimaryParent(ref).getParentRef();
        model.put("space", new TemplateNode(parent, serviceRegistry, null));
        model.put("date", new Date());

        //also add all of the node properties to the model
        model.put("properties", ns.getProperties(ref));
        
        return model;
    }
    
    /**
     * Determines whether or not a watermark should be applied to a given page
     * 
     * @param pages
     * @param current
     * @param numpages
     * @return
     */
    private boolean checkPage(String pages, int current, int numpages)
    {

    	
        boolean markPage = false;

        if (pages.equals(PAGE_EVEN))
        {
            if (current % 2 == 0)
            {
                markPage = true;
            }
        }
        else if (pages.equals(PAGE_ODD))
        {
            if (current % 2 != 0)
            {
                markPage = true;
            }
        }
        else if (pages.equals(PAGE_FIRST))
        {
            if (current == 1)
            {
                markPage = true;
            }
        }
        else if (pages.equals(PAGE_LAST))
        {
            if (current == numpages)
            {
                markPage = true;
            }
        }
        else if (pages.equals(PAGE_ALL))
        {
            markPage = true;
        }
        else
        {
        	// if we get here, a scheme wasn't selected, so we can treat this like a page list
        	List<Integer> pageList = parsePageList(pages);
        	if(pageList.contains(current))
        	{
        		markPage = true;
        	}
        }

        return markPage;
    }


    /**
     * Format the page numbers according to the localized string in messages
     * 
     * @param currentPage
     * @param lastPage
     * @return
     */
    private String formatPageNumbering(int currentPage, int lastPage)
    {
    	String text = "";
    	if (lastPage==0) 
    	{
    		text = I18NUtil.getMessage(MSGID_PAGE_NUMBERING_PATTERN_SINGLE, new Object[]{currentPage});
    	}
    	else
    	{
    		text = I18NUtil.getMessage(MSGID_PAGE_NUMBERING_PATTERN_MULTIPLE, new Object[]{currentPage, lastPage});
    	}
    	return text;
    }
    
    /**
     * @param serviceRegistry
     */
    public void setServiceRegistry(ServiceRegistry serviceRegistry)
    {
        this.serviceRegistry = serviceRegistry;
        ns = serviceRegistry.getNodeService();
        cs = serviceRegistry.getContentService();
        ffs = serviceRegistry.getFileFolderService();
        ds = serviceRegistry.getDictionaryService();
        ps = serviceRegistry.getPersonService();
        as = serviceRegistry.getAuthenticationService();
    }
    
    /**
     * Sets whether a PDF action creates a new empty node or copies the source node, preserving
     * the content type, applied aspects and properties
     * 
     * @param createNew
     */
    public void setCreateNew(boolean createNew)
    {
    	this.createNew = createNew;
    }
    
    public void setUseSignatureAspect(boolean useSignatureAspect)
    {
    	this.useSignatureAspect = useSignatureAspect;
    }
    
    public void setUseEncryptionAspect(boolean useEncryptionAspect)
    {
    	this.useEncryptionAspect = useEncryptionAspect;
    }

    public void setUseFirstCommentAspect(boolean useFirstCommentAspect){
        this.useFirstCommentAspect = useFirstCommentAspect;
    }

    public void setUseSecondCommentAspect(boolean useSecondCommentAspect){
        this.useSecondCommentAspect = useSecondCommentAspect;
    }

    public void setUseThirdCommentAspect(boolean useThirdCommentAspect){
        this.useThirdCommentAspect = useThirdCommentAspect;
    }

    public void setUseFifthCommentAspect(boolean useFifthCommentAspect){
        this.useFifthCommentAspect = useFifthCommentAspect;
    }

    public void setUseSixthCommentAspect(boolean useSixthCommentAspect){
        this.useSixthCommentAspect = useSixthCommentAspect;
    }
}

