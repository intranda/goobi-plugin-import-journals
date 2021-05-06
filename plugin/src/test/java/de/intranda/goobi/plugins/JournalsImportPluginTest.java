package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.easymock.EasyMock;
import org.goobi.production.enums.ImportType;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class, ConfigOpac.class, ConfigurationHelper.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*" })
public class JournalsImportPluginTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private File tempFolder;
    private String resourcesFolder;

    @Before
    public void setUp() throws Exception {
        tempFolder = folder.newFolder("tmp");

        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        PowerMock.mockStatic(ConfigPlugins.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(getConfig()).anyTimes();
        PowerMock.replay(ConfigPlugins.class);

        ConfigOpacDoctype cod = new ConfigOpacDoctype("periodical", "Periodical", "Periodical", true, false, false, new HashMap<>(),
                new ArrayList<>(), "PeriodicalVolume");

        ConfigOpac configOpacMock = EasyMock.createMock(ConfigOpac.class);
        EasyMock.expect(configOpacMock.getAllCatalogues()).andReturn(getAllCatalogues()).anyTimes();
        EasyMock.expect(configOpacMock.getDoctypeByMapping(EasyMock.anyString(), EasyMock.anyString())).andReturn(cod).anyTimes();
        EasyMock.replay(configOpacMock);

        PowerMock.mockStatic(ConfigOpac.class);
        EasyMock.expect(ConfigOpac.getInstance()).andReturn(configOpacMock).anyTimes();
        PowerMock.replay(ConfigOpac.class);

        ConfigurationHelper configurationHelperMock = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(configurationHelperMock.isUseProxy()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelperMock.getDebugFolder()).andReturn("").anyTimes();
        EasyMock.expect(configurationHelperMock.getConfigurationFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.replay(configurationHelperMock);

        PowerMock.mockStatic(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelperMock).anyTimes();
        PowerMock.replay(ConfigurationHelper.class);
    }

    @Test
    public void testConstructor() {
        JournalsImportPlugin plugin = new JournalsImportPlugin();
        assertNotNull(plugin);
        assertEquals(ImportType.FOLDER, plugin.getImportTypes().get(0));
        plugin.setImportFolder(tempFolder.getAbsolutePath());
    }

    @Test
    public void testConfigurationFile() {
        JournalsImportPlugin plugin = new JournalsImportPlugin();
        assertTrue(plugin.isRunnableAsGoobiScript());
        assertEquals("K10+", plugin.getCatalogueName());
        assertEquals("src/test/resources", plugin.getBasedir());
    }

    @Test
    public void testGetAllFilenames() {
        JournalsImportPlugin plugin = new JournalsImportPlugin();
        assertTrue(plugin.isRunnableAsGoobiScript());

        List<String> foldernames = plugin.getAllFilenames();
        assertEquals(1, foldernames.size());
        assertEquals("170621391", foldernames.get(0));
    }

    @Test
    public void testGenerateRecordsFromFilenames() {
        JournalsImportPlugin plugin = new JournalsImportPlugin();
        assertTrue(plugin.isRunnableAsGoobiScript());

        List<String> foldernames = plugin.getAllFilenames();
        List<Record> fixture = plugin.generateRecordsFromFilenames(foldernames);
        assertEquals(1, fixture.size());
        assertEquals("170621391", fixture.get(0).getId());
        assertEquals("170621391", fixture.get(0).getData());
    }

    @Test
    public void testGetRecordFromCatalogue() throws Exception {
        JournalsImportPlugin plugin = new JournalsImportPlugin();
        assertTrue(plugin.isRunnableAsGoobiScript());
        Prefs prefs = new Prefs();
        prefs.loadPrefs(resourcesFolder + "ruleset.xml");
        plugin.setPrefs(prefs);

        Fileformat ff = plugin.getRecordFromCatalogue("170621391");

        assertEquals("Periodical", ff.getDigitalDocument().getLogicalDocStruct().getType().getName());
    }

    @Test
    public void testGenerateFiles() throws Exception {
        JournalsImportPlugin plugin = new JournalsImportPlugin();
        assertTrue(plugin.isRunnableAsGoobiScript());
        plugin.setWorkflowTitle("666");
        Prefs prefs = new Prefs();
        prefs.loadPrefs(resourcesFolder + "ruleset.xml");
        plugin.setPrefs(prefs);

        plugin.setImportFolder(tempFolder.toString());

        List<String> foldernames = plugin.getAllFilenames();
        List<Record> records = plugin.generateRecordsFromFilenames(foldernames);
        List<ImportObject> fixture = plugin.generateFiles(records);
        ImportObject io = fixture.get(0);


    }

    private XMLConfiguration getConfig() {
        String file = "plugin_intranda_import_journals.xml";
        XMLConfiguration config = new XMLConfiguration();
        config.setDelimiterParsingDisabled(true);
        try {
            config.load(file);
        } catch (ConfigurationException e) {
        }
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        return config;
    }

    private List<ConfigOpacCatalogue> getAllCatalogues() {
        List<ConfigOpacCatalogue> catalogues = new ArrayList<>();
        ConfigOpacCatalogue coc = new ConfigOpacCatalogue("K10+", "K 10 plus", "kxp.k10plus.de", "2.1", "iktlist.xml", 80, "utf8",
                "&UCNF=NFC&XPNOFF=1", new ArrayList<>(), "pica", "https://", new HashMap<>());
        coc.setOpacPlugin(new PicaOpacImport());
        catalogues.add(coc);
        return catalogues;
    }
}
