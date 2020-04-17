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

package org.vividus.selenium;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.browserup.bup.client.ClientUtil;

import org.jbehave.core.model.Meta;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.vividus.bdd.context.IBddRunContext;
import org.vividus.bdd.model.MetaWrapper;
import org.vividus.bdd.model.RunningStory;
import org.vividus.proxy.IProxy;
import org.vividus.selenium.manager.IWebDriverManagerContext;
import org.vividus.selenium.manager.WebDriverManagerParameter;

public abstract class AbstractVividusDriverFactory implements IVividusDriverFactory
{
    private final IBddRunContext bddRunContext;
    private final IWebDriverManagerContext webDriverManagerContext;
    private final IProxy proxy;

    private boolean remoteExecution;

    public AbstractVividusDriverFactory(IBddRunContext bddRunContext, IWebDriverManagerContext webDriverManagerContext,
            IProxy proxy)
    {
        this.bddRunContext = bddRunContext;
        this.webDriverManagerContext = webDriverManagerContext;
        this.proxy = proxy;
    }

    @Override
    public VividusWebDriver create()
    {
        VividusWebDriver vividusWebDriver = createVividusWebDriver(bddRunContext.getRunningStory());

        DesiredCapabilities desiredCapabilities = vividusWebDriver.getDesiredCapabilities();
        if (proxy.isStarted())
        {
            desiredCapabilities.setCapability(CapabilityType.PROXY, createSeleniumProxy(remoteExecution));
            desiredCapabilities.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
        }
        configureVividusWebDriver(vividusWebDriver);
        return vividusWebDriver;
    }

    protected abstract void configureVividusWebDriver(VividusWebDriver vividusWebDriver);

    private Proxy createSeleniumProxy(boolean remoteExecution)
    {
        try
        {
            return ClientUtil.createSeleniumProxy(proxy.getProxyServer(),
                    remoteExecution ? InetAddress.getLocalHost() : InetAddress.getLoopbackAddress());
        }
        catch (UnknownHostException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private VividusWebDriver createVividusWebDriver(RunningStory runningStory)
    {
        VividusWebDriver vividusWebDriver = new VividusWebDriver();
        setBaseDesiredCapabilities(vividusWebDriver, runningStory);

        return vividusWebDriver;
    }

    private void setBaseDesiredCapabilities(VividusWebDriver vividusWebDriver, RunningStory runningStory)
    {
        DesiredCapabilities desiredCapabilities = vividusWebDriver.getDesiredCapabilities();
        desiredCapabilities.merge(webDriverManagerContext.getParameter(
                WebDriverManagerParameter.DESIRED_CAPABILITIES));
        webDriverManagerContext.reset(WebDriverManagerParameter.DESIRED_CAPABILITIES);
        if (runningStory != null)
        {
            Scenario scenario = runningStory.getRunningScenario().getScenario();
            Meta mergedMeta = mergeMeta(runningStory.getStory(), scenario);
            MetaWrapper metaWrapper = new MetaWrapper(mergedMeta);
            ControllingMetaTag.setDesiredCapabilitiesFromMeta(desiredCapabilities, metaWrapper);
            setDesiredCapabilities(desiredCapabilities, runningStory, scenario, metaWrapper);
        }
    }

    protected abstract void setDesiredCapabilities(DesiredCapabilities desiredCapabilities, RunningStory runningStory,
            Scenario scenario, MetaWrapper metaWrapper);

    private static Meta mergeMeta(Story story, Scenario scenario)
    {
        Meta storyMeta = story.getMeta();
        return scenario == null ? storyMeta : scenario.getMeta().inheritFrom(storyMeta);
    }

    public void setRemoteExecution(boolean remoteExecution)
    {
        this.remoteExecution = remoteExecution;
    }

    protected boolean isRemoteExecution()
    {
        return remoteExecution;
    }
}
