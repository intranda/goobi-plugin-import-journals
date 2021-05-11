package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.goobi.production.properties.ImportProperty;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class JournalsImportPlugin implements IImportPluginVersion2 {

    @Getter
    private String title = "intranda_import_journals";
    @Getter
    private PluginType type = PluginType.Import;

    @Getter
    private List<ImportType> importTypes;

    /*
    - get root folder from configuration file
    - get all journals/newspaper folder within root folder

    - get ppn from selected folders
    - opac call - kxp or zdb? get catalogue name from config
    - get volume folder within newspaper folder
    - create process for each volume - year/volume number/volume id is taken from folder name
    - check if the volume contains images or sub folder
        - images -> import images to volume
        - sub folder -> create issues for each folder
    - delete imported files
     */

    @Getter
    @Setter
    private Prefs prefs;
    @Getter
    @Setter
    private String importFolder;

    @Getter
    private String catalogueName;
    @Getter
    private String basedir;

    @Setter
    private MassImportForm form;

    @Setter
    private boolean testMode = false;

    @Getter
    @Setter
    private File file;

    @Setter
    private String workflowTitle;

    private boolean runAsGoobiScript = false;

    private String imageImportStrategy;

    private String collection;

    /**
     * define what kind of import plugin this is
     */
    public JournalsImportPlugin() {
        importTypes = new ArrayList<>();
        importTypes.add(ImportType.FOLDER);
    }

    /**
     * read the configuration file
     */
    private void readConfig() {
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;
        try {
            myconfig = xmlConfig.configurationAt("//config[./template = '" + workflowTitle + "']");
        } catch (IllegalArgumentException e) {
            myconfig = xmlConfig.configurationAt("//config[./template = '*']");
        }

        if (myconfig != null) {
            runAsGoobiScript = myconfig.getBoolean("/runAsGoobiScript", false);
            collection = myconfig.getString("/collection", "");
            basedir = myconfig.getString("/importFolder", "");
            catalogueName = myconfig.getString("/catalogueName", "");
            imageImportStrategy = myconfig.getString("/imageImportStrategy", "copy");
        }
    }

    /**
     * This method is used to generate records based on the imported data these records will then be used later to generate the Goobi processes
     */
    @Override
    public List<Record> generateRecordsFromFile() {
        return null;
    }

    /**
     * This method is used to actually create the Goobi processes this is done based on previously created records
     */
    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        // some general preparations
        DocStructType issueType = prefs.getDocStrctTypeByName("PeriodicalIssue");
        DocStructType pageType = prefs.getDocStrctTypeByName("page");

        // log + phys page no
        MetadataType physPageNumberType = prefs.getMetadataTypeByName("physPageNumber");
        MetadataType logicalPageNumberType = prefs.getMetadataTypeByName("logicalPageNumber");

        MetadataType pulicationYearType = prefs.getMetadataTypeByName("PublicationYear");
        MetadataType currentNoType = prefs.getMetadataTypeByName("CurrentNo");
        MetadataType currentNoSortType = prefs.getMetadataTypeByName("CurrentNoSorting");
        MetadataType titleType = prefs.getMetadataTypeByName("TitleDocMain");

        //        MetadataType catalogIdSourceType = prefs.getMetadataTypeByName("CatalogIDSource");
        MetadataType collectionType = prefs.getMetadataTypeByName("singleDigCollection");

        MetadataType catalogIdDigitalType = prefs.getMetadataTypeByName("CatalogIDDigital");

        List<ImportObject> answer = new ArrayList<>();

        // run through all records and create a Goobi process for each of it
        for (Record record : records) {

            String folderName = record.getId();
            List<String> subFolder = new ArrayList<>();
            try {
                Files.find(Paths.get(basedir, folderName), 1, (p, file) -> file.isDirectory() && p.getFileName().toString().matches("\\d+X?_\\d{4}")
                        || p.getFileName().toString().matches("\\d{4}")).forEach(p -> subFolder.add(p.getFileName().toString()));
            } catch (IOException e) {
                log.error(e);
            }

            for (String volumeFolder : subFolder) {

                // check, if volumeFolder contains images or sub folder
                List<Path> images = new ArrayList<>();

                Path currentFolder = Paths.get(basedir, folderName, volumeFolder);

                try {
                    Files.find(currentFolder, 2, (p, file) -> file.isRegularFile()).forEach(p -> images.add(p));
                } catch (IOException e) {
                    log.error(e);
                }
                if (images.isEmpty()) {
                    // nothing to import, skip folder
                    continue;
                }

                Fileformat fileformat = getRecordFromCatalogue(record.getId());
                ImportObject io = new ImportObject();
                answer.add(io);
                if (fileformat == null) {
                    // set current import to error and continue with next one
                    io.setErrorMessage("Cannot get opac data for '" + volumeFolder + "'");
                    io.setImportReturnValue(ImportReturnValue.InvalidData);
                    io.setProcessTitle(volumeFolder);
                    continue;
                }
                try {
                    DigitalDocument digDoc = fileformat.getDigitalDocument();
                    DocStruct anchor = digDoc.getLogicalDocStruct();
                    DocStruct volume = anchor.getAllChildren().get(0);
                    DocStruct physical = digDoc.getPhysicalDocStruct();
                    String year = volumeFolder.replace(record.getId(), "").replace("_", "");

                    // check if anchor id is missing
                    List<? extends Metadata> anchorIds = anchor.getAllMetadataByType(catalogIdDigitalType);
                    if (anchorIds.isEmpty()) {
                        Metadata anchorIdentifier = new Metadata(catalogIdDigitalType);
                        anchorIdentifier.setValue(folderName);
                        anchor.addMetadata(anchorIdentifier);
                    }

                    // add PublicationYear, CurrentNo and CurrentNoSorting to volume
                    Metadata publicationYear = new Metadata(pulicationYearType);
                    publicationYear.setValue(year);
                    volume.addMetadata(publicationYear);

                    Metadata currentNo = new Metadata(currentNoType);
                    currentNo.setValue(year);
                    volume.addMetadata(currentNo);

                    Metadata currentNoSort = new Metadata(currentNoSortType);
                    currentNoSort.setValue(year);
                    volume.addMetadata(currentNoSort);

                    Metadata volumeIdentifier = new Metadata(catalogIdDigitalType);
                    volumeIdentifier.setValue(folderName + "_" + year);
                    volume.addMetadata(volumeIdentifier);

                    int physicalOrderNumber = 1;
                    for (Path image : images) {

                        // create image element
                        DocStruct dsPage = digDoc.createDocStruct(pageType);
                        Metadata physNo = new Metadata(physPageNumberType);
                        physNo.setValue(String.valueOf(physicalOrderNumber));
                        physicalOrderNumber++;
                        dsPage.addMetadata(physNo);

                        dsPage.setImageName(image.getFileName().toString());

                        Metadata logicalPageNumber = new Metadata(logicalPageNumberType);
                        logicalPageNumber.setValue("uncounted");
                        dsPage.addMetadata(logicalPageNumber);

                        // add image to the volume
                        physical.addChild(dsPage);

                        // check if image is in a sub folder
                        String parentFolder = image.getParent().getFileName().toString();
                        if (!parentFolder.equals(volumeFolder)) {
                            // image belongs to an issue
                            // check if issue exists
                            DocStruct currentIssue = null;
                            if (volume.getAllChildren() != null) {
                                for (DocStruct issue : volume.getAllChildren()) {
                                    // get issue title, compare it with folder name
                                    for (Metadata md : issue.getAllMetadata()) {
                                        if (md.getType().getName().equals(titleType.getName())) {
                                            if (md.getValue().equals(parentFolder)) {
                                                currentIssue = issue;
                                            }
                                        }
                                    }
                                }
                            }
                            // or create it
                            if (currentIssue == null) {
                                currentIssue = digDoc.createDocStruct(issueType);
                                volume.addChild(currentIssue);
                                Metadata title = new Metadata(titleType);
                                title.setValue(parentFolder);
                                currentIssue.addMetadata(title);
                            }
                            dsPage.setImageName(parentFolder.replaceAll("\\W", "") + "_" + image.getFileName().toString());
                            // add image to issue and volume
                            currentIssue.addReferenceTo(dsPage, "logical_physical");
                        }

                        volume.addReferenceTo(dsPage, "logical_physical");
                    }

                    if (record.getCollections() != null && !record.getCollections().isEmpty()) {
                        // use selected collection
                        for (String col : record.getCollections()) {
                            if (StringUtils.isNotBlank(col)) {
                                Metadata md = new Metadata(collectionType);
                                md.setValue(col);
                                anchor.addMetadata(md);
                                md = new Metadata(collectionType);
                                md.setValue(col);
                                volume.addMetadata(md);
                            }
                        }
                    } else if (StringUtils.isNotBlank(collection)) {
                        // use configured collection
                        Metadata md = new Metadata(collectionType);
                        md.setValue(collection);
                        anchor.addMetadata(md);
                        md = new Metadata(collectionType);
                        md.setValue(collection);
                        volume.addMetadata(md);
                    }

                    // save mets file,
                    String metsfilename = Paths.get(importFolder, folderName + "_" + year + ".xml").toString();
                    MetsMods mm = new MetsMods(prefs);
                    mm.setDigitalDocument(digDoc);
                    mm.write(metsfilename);
                    io.setMetsFilename(metsfilename);
                    io.setProcessTitle(folderName + "_" + year + ".xml");
                    io.setImportReturnValue(ImportReturnValue.ExportFinished);
                    // copy/move images, use new file names

                    if (!"ignore".equalsIgnoreCase(imageImportStrategy)) {

                        String foldername = metsfilename.replace(".xml", "");

                        String folderNameRule = ConfigurationHelper.getInstance().getProcessImagesMasterDirectoryName();
                        folderNameRule = folderNameRule.replace("{processtitle}", io.getProcessTitle());

                        Path path = Paths.get(foldername, "images", folderNameRule);
                        try {
                            Files.createDirectories(path);
                            for (Path image : images) {
                                Path destination = null;
                                String parentFolder = image.getParent().getFileName().toString();
                                if (!parentFolder.equals(volumeFolder)) {
                                    destination =
                                            Paths.get(path.toString(), parentFolder.replaceAll("\\W", "") + "_" + image.getFileName().toString());
                                } else {
                                    destination = Paths.get(path.toString(), image.getFileName().toString());
                                }

                                if ("copy".equalsIgnoreCase(imageImportStrategy)) {
                                    StorageProvider.getInstance().copyFile(image, destination);
                                } else if ("move".equalsIgnoreCase(imageImportStrategy)) {
                                    StorageProvider.getInstance().move(image, destination);
                                }
                            }
                        } catch (IOException e) {
                            log.error(e);
                        }
                    }
                    // cleanup
                    if ("move".equalsIgnoreCase(imageImportStrategy)) {
                        // remove empty folder if imageImportStrategy was set to move
                        // check if volumeFolder contains empty sub folder, remove them
                        List<Path> subdirs = StorageProvider.getInstance().listFiles(currentFolder.toString());
                        for (Path dir : subdirs) {
                            try {
                                Files.delete(dir);
                            } catch (IOException e) {
                                // folder cannot be deleted, probably because it is not empty
                                log.error(e);
                            }
                        }
                        // check if volumeFolder is empty, remove it
                        try {
                            Files.delete(currentFolder);
                        } catch (IOException e) {
                            // folder cannot be deleted, probably because it is not empty
                            log.error(e);
                        }
                    }
                } catch (UGHException e) {
                    log.error(e);
                    io.setErrorMessage("Cannot add additional metadata to '" + volumeFolder + "'");
                    io.setImportReturnValue(ImportReturnValue.InvalidData);


                }
            }
            // now add the process to the list
        }
        return answer;
    }

    public Fileformat getRecordFromCatalogue(String id) {
        // opac request for anchor id
        IOpacPlugin myImportOpac = null;
        ConfigOpacCatalogue coc = null;
        ConfigOpac co = ConfigOpac.getInstance();
        for (ConfigOpacCatalogue configOpacCatalogue : co.getAllCatalogues()) {
            if (configOpacCatalogue.getTitle().equals(catalogueName)) {
                myImportOpac = configOpacCatalogue.getOpacPlugin();
                coc = configOpacCatalogue;
            }
        }
        if (myImportOpac == null) {
            return null;
        }
        Fileformat fileformat = null;
        try {
            fileformat = myImportOpac.search("12", id, coc, prefs);
        } catch (Exception e) {
            log.error(e);
        }
        return fileformat;
    }

    /**
     * decide if the import shall be executed in the background via GoobiScript or not
     */
    @Override
    public boolean isRunnableAsGoobiScript() {
        readConfig();
        return runAsGoobiScript;
    }

    /* *************************************************************** */
    /*                                                                 */
    /* the following methods are mostly not needed for typical imports */
    /*                                                                 */
    /* *************************************************************** */

    @Override
    public List<Record> splitRecords(String string) {
        List<Record> answer = new ArrayList<>();
        return answer;
    }

    @Override
    public List<String> splitIds(String ids) {
        return null;
    }

    @Override
    public String addDocstruct() {
        return null;
    }

    @Override
    public String deleteDocstruct() {
        return null;
    }

    @Override
    public void deleteFiles(List<String> arg0) {
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        List<Record> records = new ArrayList<>();
        for (String filename : filenames) {
            Record rec = new Record();
            rec.setData(filename);
            rec.setId(filename);
            records.add(rec);
        }
        return records;
    }

    @Override
    public List<String> getAllFilenames() {
        List<String> foldernames = new ArrayList<>();
        try {
            Files.find(Paths.get(basedir), 1, (p, file) -> file.isDirectory() && p.getFileName().toString().matches("\\d+X?"))
            .forEach(p -> foldernames.add(p.getFileName().toString()));
        } catch (IOException e) {
            log.error(e);
        }

        return foldernames;
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null;
    }

    @Override
    public String getProcessTitle() {
        return null;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null;
    }

    @Override
    public void setData(Record arg0) {
    }

    @Override
    public void setDocstruct(DocstructElement arg0) {
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        return null;
    }

}