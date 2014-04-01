package undertow4jenkins;

import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.deployment;
import io.undertow.Undertow;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.xml.stream.XMLStreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import undertow4jenkins.loader.ErrorPageLoader;
import undertow4jenkins.loader.FilterLoader;
import undertow4jenkins.loader.ListenerLoader;
import undertow4jenkins.loader.MimeLoader;
import undertow4jenkins.loader.ServletLoader;
import undertow4jenkins.option.OptionParser;
import undertow4jenkins.option.Options;
import undertow4jenkins.parser.WebXmlContent;
import undertow4jenkins.parser.WebXmlFormatException;
import undertow4jenkins.parser.WebXmlParser;
import undertow4jenkins.util.WarWorker;

/**
 * @author Jakub Bartecek <jbartece@redhat.com>
 * 
 */
public class Launcher {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Options options;

    private ClassLoader jenkinsWarClassLoader;

    private final String pathToTmpDir = "/tmp/undertow4jenkins/extractedWar/";

    /**
     * Field for usage, which can be overridden outside this class
     */
    public static String USAGE;

    public Launcher(Options options) {
        this.options = options;
        log.info(options.toString());
    }

    public void run() {
        try {
            WarWorker.extractFilesFromWar(options.warfile, pathToTmpDir);
            // Create class loader to load classed from jenkins.war archive.
            // It is needed to load servlet classes such as Stapler.
            this.jenkinsWarClassLoader = WarWorker.createJarsClassloader(options.warfile,
                    pathToTmpDir);

            WebXmlParser parser = new WebXmlParser();
            WebXmlContent webXmlContent = parser.parse(pathToTmpDir + "WEB-INF/web.xml");
            
            log.debug(webXmlContent.toString());
            
            startUndertow();
        } catch (ServletException e) {
            log.error("Start of embedded Undertow server failed!", e);
        } catch (IOException e) {
            log.error("War archive extraction failed!", e);
        } catch (ClassNotFoundException e) {
            log.error("Initiating servlet container failed!", e);
        } catch (XMLStreamException e) {
            log.error("Parsing web.xml failed!", e);
        } catch( WebXmlFormatException e) {
            log.error("Parsing web.xml failed!", e);
        }

    }

    private void startUndertow() throws ServletException, ClassNotFoundException {

        DeploymentManager manager = defaultContainer().addDeployment(
                createServletContainerDeployment());
        manager.deploy();

        Undertow server = Undertow.builder()
                .addHttpListener(options.httpPort, "localhost")
                // .addAjpListener(options.ajp13Port, options.ajp13ListenAdress)
                .setHandler(manager.start())
                .setWorkerThreads(options.handlerCountMax) // TODO
                .build();
        server.start();

    }

    private DeploymentInfo createServletContainerDeployment() throws ClassNotFoundException {
        DeploymentInfo servletContainerBuilder = deployment()
                .setClassLoader(jenkinsWarClassLoader)
                .setContextPath("")
                .setMajorVersion(2)
                .setMinorVersion(4)
                .setDeploymentName("Jenkins CI")
                .addListener(
                        ListenerLoader.createListener("hudson.WebAppMain", jenkinsWarClassLoader))
                .addServlets(ServletLoader.createServlets(jenkinsWarClassLoader))
                .addErrorPage(ErrorPageLoader.createErrorPage())
                .addFilters(FilterLoader.createFilters(jenkinsWarClassLoader))
                .addMimeMappings(MimeLoader.createMimeMappings());

        FilterLoader.addFilterMappings(servletContainerBuilder);

        // Load static resources from extracted war archive
        servletContainerBuilder.setResourceManager(
                new FileResourceManager(new File(pathToTmpDir), 0L));

        return servletContainerBuilder;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        Logger log = LoggerFactory.getLogger("Main");
        log.info("Undertow4Jenkins is starting...");

        OptionParser optionParser = new OptionParser();
        Options options = optionParser.parse(args);

        Launcher launcher = new Launcher(options);
        launcher.run();
    }

}
