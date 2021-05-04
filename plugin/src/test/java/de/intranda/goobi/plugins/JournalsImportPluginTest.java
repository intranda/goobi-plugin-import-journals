package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.easymock.EasyMock;
import org.goobi.production.enums.ImportType;
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

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class })
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
        assertEquals(1,foldernames.size());
        assertEquals("170621391", foldernames.get(0));


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

}
