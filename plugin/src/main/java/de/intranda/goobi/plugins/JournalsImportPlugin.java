package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.goobi.production.properties.ImportProperty;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;

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
    @SuppressWarnings("unchecked")
    public List<ImportObject> generateFiles(List<Record> records) {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        // some general preparations
        DocStructType physicalType = prefs.getDocStrctTypeByName("BoundBook");
        DocStructType anchorType = prefs.getDocStrctTypeByName("Periodical");
        DocStructType volumeType = prefs.getDocStrctTypeByName("PeriodicalVolume");
        DocStructType issueType = prefs.getDocStrctTypeByName("PeriodicalIssue");

        MetadataType pathimagefilesType = prefs.getMetadataTypeByName("pathimagefiles");
        List<ImportObject> answer = new ArrayList<>();

        // run through all records and create a Goobi process for each of it
        for (Record record : records) {

            String folderName = record.getId();
            List<String> subFolder = null;
            Fileformat myRdf = getRecordFromCatalogue(record.getId());

            for (String volume : subFolder) {

            }

            ImportObject io = new ImportObject();

            String id = record.getId().replaceAll("\\W", "_");
            HashMap<String, String> map = (HashMap<String, String>) record.getObject();

            // create a new mets file
            //            try {
            //                Fileformat fileformat = new MetsMods(prefs);
            //
            //                // create digital document
            //                DigitalDocument dd = new DigitalDocument();
            //                fileformat.setDigitalDocument(dd);
            //
            //                // create physical DocStruct
            //                DocStruct physical = dd.createDocStruct(physicalType);
            //                dd.setPhysicalDocStruct(physical);
            //
            //                // set imagepath
            //                Metadata newmd = new Metadata(pathimagefilesType);
            //                newmd.setValue("/images/");
            //                physical.addMetadata(newmd);
            //
            //                // create logical DocStruct
            //                DocStruct logical = dd.createDocStruct(logicalType);
            //                dd.setLogicalDocStruct(logical);
            //
            //                // create metadata field for CatalogIDDigital with cleaned value
            //                Metadata md1 = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
            //                md1.setValue(map.get("ID").replaceAll("\\W", "_"));
            //                logical.addMetadata(md1);
            //
            //                // create metadata field for main title
            //                Metadata md2 = new Metadata(prefs.getMetadataTypeByName("TitleDocMain"));
            //                md2.setValue(map.get("Title"));
            //                logical.addMetadata(md2);
            //
            //                // create metadata field for year
            //                Metadata md3 = new Metadata(prefs.getMetadataTypeByName("PublicationYear"));
            //                md3.setValue(map.get("Year"));
            //                logical.addMetadata(md3);
            //
            //                // add author
            //                Person per = new Person(prefs.getMetadataTypeByName("Author"));
            //                per.setFirstname(map.get("Author first name"));
            //                per.setLastname(map.get("Author last name"));
            //                //per.setRole("Author");
            //                logical.addPerson(per);
            //
            //                // create metadata field for configured digital collection
            //                MetadataType typeCollection = prefs.getMetadataTypeByName("singleDigCollection");
            //                if (StringUtils.isNotBlank(collection)) {
            //                    Metadata mdc = new Metadata(typeCollection);
            //                    mdc.setValue(collection);
            //                    logical.addMetadata(mdc);
            //                }
            //
            //                // and add all collections that where selected
            //                if (form != null) {
            //                    for (String c : form.getDigitalCollections()) {
            //                        if (!c.equals(collection.trim())) {
            //                            Metadata md = new Metadata(typeCollection);
            //                            md.setValue(c);
            //                            logical.addMetadata(md);
            //                        }
            //                    }
            //                }
            //
            //                // set the title for the Goobi process
            //                io.setProcessTitle(id);
            //                String fileName = getImportFolder() + File.separator + io.getProcessTitle() + ".xml";
            //                io.setMetsFilename(fileName);
            //                fileformat.write(fileName);
            //                io.setImportReturnValue(ImportReturnValue.ExportFinished);
            //            } catch (UGHException e) {
            //                log.error("Error while creating Goobi processes in the JournalsImportPlugin", e);
            //                io.setImportReturnValue(ImportReturnValue.WriteError);
            //            }

            // now add the process to the list
            answer.add(io);
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