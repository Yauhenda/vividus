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

import java.net.URL;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.vividus.selenium.manager.DriverManager;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;

public class RemoteWebDriverFactory implements IRemoteWebDriverFactory
{
    @Override
    public RemoteWebDriver getRemoteWebDriver(URL url, Capabilities capabilities)
    {
        if (DriverManager.isIOS(capabilities))
        {
            return new IOSDriver<>(url, capabilities);
        }
        else if (DriverManager.isAndroid(capabilities))
        {
            return new AndroidDriver<>(url, capabilities);
        }
        return new RemoteWebDriver(url, capabilities);
    }
}