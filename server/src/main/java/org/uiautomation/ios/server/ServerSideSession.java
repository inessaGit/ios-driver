package org.uiautomation.ios.server;

import java.io.File;
import java.net.URL;
import java.util.UUID;

import org.uiautomation.ios.IOSCapabilities;
import org.uiautomation.ios.UIAModels.Session;
import org.uiautomation.ios.UIAModels.UIADriver;
import org.uiautomation.ios.UIAModels.configuration.DriverConfiguration;
import org.uiautomation.ios.UIAModels.configuration.WorkingMode;
import org.uiautomation.ios.client.uiamodels.impl.RemoteUIADriver;
import org.uiautomation.ios.exceptions.SessionNotCreatedException;
import org.uiautomation.ios.mobileSafari.WebInspector;
import org.uiautomation.ios.server.application.IOSApplication;
import org.uiautomation.ios.server.configuration.DriverConfigurationStore;
import org.uiautomation.ios.server.instruments.CommunicationChannel;
import org.uiautomation.ios.server.instruments.InstrumentsManager;
import org.uiautomation.ios.server.utils.ClassicCommands;

public class ServerSideSession extends Session {

  private final IOSApplication application;
  private final IOSCapabilities capabilities;
  private final InstrumentsManager instruments;
  public final IOSDriver driver;

  private WebInspector inspector;
  private UIADriver nativeDriver;

  private final Context context;

  private final DriverConfiguration nativeConf;
  private final DriverConfiguration webConf;

  ServerSideSession(IOSDriver driver, IOSCapabilities capabilities) {
    super(UUID.randomUUID().toString());
    this.driver = driver;
    this.capabilities = capabilities;

    application = driver.findMatchingApplication(capabilities);
    application.setLanguage(capabilities.getLanguage());
    if (capabilities.getSDKVersion() == null) {
      capabilities.setSDKVersion(ClassicCommands.getDefaultSDK());
    } else {
      String version = capabilities.getSDKVersion();
      if (!driver.getHostInfo().getInstalledSDKs().contains(version)) {
        throw new SessionNotCreatedException("Cannot start on version " + version + ".Installed : "
            + driver.getHostInfo().getInstalledSDKs());
      }
    }
    instruments = new InstrumentsManager(driver.getPort());
    context = new Context();
    nativeConf = new DriverConfigurationStore();
    webConf = new DriverConfigurationStore();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        forceStop();
      }
    });
  }

  public DriverConfiguration getConf(WorkingMode mode) {
    switch (mode) {
    case Native:
      return nativeConf;
    case Web:
      return webConf;
    default:
      throw new RuntimeException("NI");
    }

  }

  public UIADriver getNativeDriver() {
    return nativeDriver;
  }

  public IOSApplication getApplication() {
    return application;
  }

  public CommunicationChannel communication() {
    return instruments.communicate();
  }

  public void stop() {
    instruments.stop();
  }

  public void forceStop() {
    instruments.forceStop();
  }

  public File getOutputFolder() {
    return instruments.getOutput();
  }

  public InstrumentsManager getInstruments() {
    return instruments;

  }

  public void start() {
    instruments.startSession(capabilities.getDevice(), capabilities.getSDKVersion(), capabilities.getLocale(),
        capabilities.getLanguage(), application.getApplicationPath(), getSessionId(), capabilities.isTimeHack(),
        capabilities.getExtraSwitches());

    URL url = null;
    try {
      url = new URL("http://localhost:" + driver.getHostInfo().getPort() + "/wd/hub");
    } catch (Exception e) {
      e.printStackTrace();
    }
    nativeDriver = new RemoteUIADriver(url, new Session(instruments.getSessionId()));
  }

  public WebInspector getWebInspector() {
    if (inspector == null) {
      String bundleId = application.getMetadata("CFBundleIdentifier");
      try {
        this.inspector = new WebInspector(nativeDriver, bundleId, this);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return inspector;
  }

  public void setMode(WorkingMode mode) {
    context.switchToMode(mode);
  }

  public WorkingMode getMode() {
    return context.getWorkingMode();
  }

  public Context getContext() {
    return context;
  }

}
