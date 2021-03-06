/*
 * Copyright 2019-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vividus.bdd.report.allure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.vividus.bdd.report.allure.AllureReportGenerator;

import io.qameta.allure.Constants;
import io.qameta.allure.ReportGenerator;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.xml.*")
public class AllureReportGeneratorTests
{
    private static final String ALLURE_CUSTOMIZATION_PATH = "/allure-customization/";
    private static final String ALLURE_CUSTOMIZATION_PATTERN = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
            + ALLURE_CUSTOMIZATION_PATH + "**";
    private static final String ALLURE_RESULTS_DIRECTORY_PROPERTY = "allure.results.directory";

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder(new File("./"));

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    private AllureReportGenerator allureReportGenerator;

    @Before
    public void before()
    {
        System.setProperty(ALLURE_RESULTS_DIRECTORY_PROPERTY, "output/allure-results");
        allureReportGenerator = new AllureReportGenerator(resourcePatternResolver);
    }

    @After
    public void after()
    {
        System.clearProperty(ALLURE_RESULTS_DIRECTORY_PROPERTY);
    }

    @Test
    public void testStart() throws IOException
    {
        File testFile = testFolder.newFile();
        File reportDirectory = testFolder.getRoot();
        allureReportGenerator.setReportDirectory(reportDirectory);
        allureReportGenerator.start();
        assertFalse(FileUtils.directoryContains(reportDirectory, testFile));
    }

    @Test
    public void testStartNoReportDirectory()
    {
        File reportDirectory = Mockito.mock(File.class);
        allureReportGenerator.setReportDirectory(reportDirectory);
        allureReportGenerator.start();
        verify(reportDirectory, never()).listFiles();
    }

    @Test
    @PrepareForTest(FileUtils.class)
    public void testStartException() throws IOException
    {
        File reportDirectory = Mockito.mock(File.class);
        allureReportGenerator.setReportDirectory(reportDirectory);
        when(reportDirectory.exists()).thenReturn(true);
        IOException ioException = Mockito.mock(IOException.class);
        PowerMockito.mockStatic(FileUtils.class);
        PowerMockito.doThrow(ioException).when(FileUtils.class);
        FileUtils.cleanDirectory(reportDirectory);
        Exception thrown = assertThrows(IllegalStateException.class, () -> allureReportGenerator.start());
        assertEquals(ioException, thrown.getCause());
    }

    @Test
    @PrepareForTest(AllureReportGenerator.class)
    public void testEndNotStarted() throws Exception
    {
        AllureReportGenerator spy = PowerMockito.spy(allureReportGenerator);
        spy.end();
        PowerMockito.verifyPrivate(spy, never()).invoke("generateReport");
    }

    @SuppressWarnings("unchecked")
    @Test
    @PrepareForTest({ReportGenerator.class, AllureReportGenerator.class, FileUtils.class})
    public void testEnd() throws Exception
    {
        File historyDirectory = testFolder.newFolder();
        allureReportGenerator.setHistoryDirectory(historyDirectory);
        File reportDirectory = testFolder.getRoot();
        allureReportGenerator.setReportDirectory(reportDirectory);
        ReportGenerator reportGenerator = PowerMockito.mock(ReportGenerator.class);
        PowerMockito.whenNew(ReportGenerator.class).withAnyArguments().thenReturn(reportGenerator);
        PowerMockito.mockStatic(FileUtils.class);
        PowerMockito.doAnswer(a ->
        {
            resolveTrendsDir(reportDirectory);
            return a;
        }).when(reportGenerator).generate(any(Path.class), any(List.class));
        String text = "text";
        when(FileUtils.readFileToString(any(File.class), eq(StandardCharsets.UTF_8))).thenReturn(text);
        allureReportGenerator.setReportDirectory(testFolder.getRoot());
        Resource resource = mockResource("/allure-customization/some_file.txt");
        Resource folder = mockResource("/allure-customization/folder/");
        Resource[] resources = { resource, folder };
        when(resourcePatternResolver.getResources(ALLURE_CUSTOMIZATION_PATTERN)).thenReturn(resources);
        allureReportGenerator.start();
        allureReportGenerator.end();
        PowerMockito.verifyStatic(FileUtils.class, never());
        FileUtils.copyInputStreamToFile(eq(folder.getInputStream()), any(File.class));
        PowerMockito.verifyStatic(FileUtils.class);
        FileUtils.copyInputStreamToFile(eq(resource.getInputStream()), any(File.class));
        PowerMockito.verifyStatic(FileUtils.class, times(2));
        FileUtils.writeStringToFile(any(File.class), eq(text), eq(StandardCharsets.UTF_8));
        PowerMockito.verifyStatic(FileUtils.class);
        FileUtils.copyDirectory(argThat(arg -> arg.getAbsolutePath().equals(resolveTrendsDir(reportDirectory))),
                eq(historyDirectory));
        verify(reportGenerator).generate(any(Path.class), any(List.class));
    }

    private Resource mockResource(String asString) throws IOException
    {
        Resource resource = Mockito.mock(Resource.class);
        URL resourceURL = PowerMockito.mock(URL.class);
        when(resource.getURL()).thenReturn(resourceURL);
        when(resourceURL.toString()).thenReturn(asString);
        InputStream stubInputStream = new ByteArrayInputStream("test data".getBytes(StandardCharsets.UTF_8));
        when(resource.getInputStream()).thenReturn(stubInputStream);
        return resource;
    }

    private String resolveTrendsDir(File dir)
    {
        try
        {
            File history = new File(dir, Constants.HISTORY_DIR);
            history.mkdir();
            File trends = new File(history, "trends");
            trends.createNewFile();
            return history.getAbsolutePath();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
