package undertow4jenkins;

import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.deployment;
import io.undertow.Undertow;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;

import java.io.File;

import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import undertow4jenkins.loader.ErrorPageLoader;
import undertow4jenkins.loader.FilterLoader;
import undertow4jenkins.loader.ListenerLoader;
import undertow4jenkins.loader.MimeLoader;
import undertow4jenkins.loader.SecurityLoader;
import undertow4jenkins.loader.ServletLoader;
import undertow4jenkins.option.Options;
import undertow4jenkins.parser.WebXmlContent;

public class UndertowInitiator {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ClassLoader classLoader;

    private Options options;

    private String pathToTmpDir;

    public UndertowInitiator(ClassLoader classLoader, Options options, String pathToTmpDir) {
        this.classLoader = classLoader;
        this.options = options;
        this.pathToTmpDir = pathToTmpDir;
    }

    public Undertow initUndertow(WebXmlContent webXmlContent) throws ServletException,
            ClassNotFoundException {

        DeploymentManager manager = defaultContainer().addDeployment(
                createServletContainerDeployment(webXmlContent));
        manager.deploy();

        Undertow server = Undertow.builder()
                .addHttpListener(options.httpPort, "localhost")
                // .addAjpListener(options.ajp13Port, options.ajp13ListenAdress)
                .setHandler(manager.start())
                .setWorkerThreads(options.handlerCountMax) // TODO
                .build();
        return server;
    }

    private DeploymentInfo createServletContainerDeployment(WebXmlContent webXmlContent)
            throws ClassNotFoundException {
        DeploymentInfo servletContainerBuilder = deployment()
                .setClassLoader(classLoader)
                .setContextPath("")
                .setDeploymentName(options.warfile)
                .addListeners(ListenerLoader.createListener(webXmlContent.listeners, classLoader))
                .addServlets(
                        ServletLoader.createServlets(webXmlContent.servlets,
                                webXmlContent.servletsMapping, classLoader))
                .addFilters(FilterLoader.createFilters(webXmlContent.filters, classLoader))
                .addSecurityRoles(SecurityLoader.createSecurityRoles(webXmlContent.securityRoles))
                .addSecurityConstraints(
                        SecurityLoader.createSecurityConstraints(webXmlContent.securityConstraints))
                .setLoginConfig(SecurityLoader.createLoginConfig(webXmlContent.loginConfig))
                .addErrorPages(ErrorPageLoader.createErrorPage(webXmlContent.errorPages))
                .addMimeMappings(MimeLoader.createMimeMappings(webXmlContent.mimeMappings));

        FilterLoader.addFilterMappings(webXmlContent.filterMappings, servletContainerBuilder);
        setServletAppVersion(webXmlContent.webAppVersion, servletContainerBuilder);
        setDisplayName(webXmlContent.displayName, servletContainerBuilder);

        // Load static resources from extracted war archive
        servletContainerBuilder.setResourceManager(
                new FileResourceManager(new File(pathToTmpDir), 0L));

        //TODO solve env-entry
        
        return servletContainerBuilder;
    }

    private void setDisplayName(String displayName, DeploymentInfo servletContainerBuilder) {
        if (displayName != null)
            servletContainerBuilder.setDisplayName(displayName);
    }

    private void setServletAppVersion(String version, DeploymentInfo servletContainerBuilder) {
        if (version == null)
            return;

        String[] versionArray = version.split("\\.");

        if (versionArray.length == 2) {
            try {
                int majorVersion = Integer.parseInt(versionArray[0]);
                int minorVersion = Integer.parseInt(versionArray[1]);
                servletContainerBuilder.setMajorVersion(majorVersion);
                servletContainerBuilder.setMinorVersion(minorVersion);
                return;
            } catch (NumberFormatException e) {
            }
        }

        log.warn("Version of web-app is not set properly!");
    }

}
