package taboolib.common.env;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;

/**
 * The class that contains all of the methods needed for downloading and
 * injecting dependencies into the classpath.
 *
 * @author Zach Deibert, sky
 * @since 1.0.0
 */
public class DependencyDownloader extends AbstractXmlParser {

    /**
     * A set of all of the dependencies that have already been injected into the
     * classpath, so they should not be reinjected (to prevent cyclic
     * dependencies from freezing the code in a loop)
     *
     * @since 1.0.0
     */
    private static final Set<Dependency> injectedDependencies = new HashSet<>();

    /**
     * The directory to download and store artifacts in
     *
     * @since 1.0.0
     */
    private File baseDir = new File("libs");

    /**
     * The scopes to download dependencies for by default
     *
     * @since 1.0.0
     */
    private DependencyScope[] dependencyScopes = {DependencyScope.RUNTIME, DependencyScope.COMPILE};

    /**
     * If debugging information should be logged to {@link System#out}
     *
     * @since 1.0.0
     */
    private boolean isDebugMode = true;

    private final Set<Repository> repositories = new HashSet<>();

    private boolean ignoreOptional = true;

    /**
     * Makes sure that the {@link DependencyDownloader#baseDir} exists
     *
     * @since 1.0.0
     */
    private void createBaseDir() {
        baseDir.mkdirs();
    }

    /**
     * Injects a set of dependencies into the classpath
     *
     * @param dependencies The dependencies to inject
     * @since 1.0.0
     */
    public void injectClasspath(Set<Dependency> dependencies) {
        for (Dependency dep : dependencies) {
            File file = dep.getFile(baseDir, "jar");
            if (file.exists()) {
                if (isDebugMode) {
                    System.out.println("Loading " + dep);
                }
                ClassAppender.addPath(file.toPath());
            }
        }
    }

    /**
     * Downloads a dependency along with all of its dependencies and stores them
     * in the {@link DependencyDownloader#baseDir}.
     *
     * @param repositories The list of repositories to try to download from
     * @param dependency   The dependency to download
     * @return The set of all dependencies that were downloaded
     * @throws IOException If an I/O error has occurred
     * @since 1.0.0
     */
    public Set<Dependency> download(Collection<Repository> repositories, Dependency dependency) throws IOException {
        if (injectedDependencies.contains(dependency)) {
            return new HashSet<>();
        }
        if (dependency.getVersion() == null) {
            IOException e = null;
            for (Repository repo : repositories) {
                try {
                    repo.setVersion(dependency);
                    e = null;
                    break;
                } catch (IOException ex) {
                    if (e == null) {
                        e = new IOException(String.format("Unable to find latest version of %s", dependency));
                    }
                    e.addSuppressed(ex);
                }
            }
            if (e != null) {
                Version max = null;
                for (Version ver : dependency.getInstalledVersions(baseDir)) {
                    if (max == null || ver.compareTo(max) > 0) {
                        max = ver;
                    }
                }
                if (max == null) {
                    throw e;
                } else {
                    dependency.setVersion(max.toString());
                }
            }
        }
        File pom = dependency.getFile(baseDir, "pom");
        File pom1 = new File(pom.getPath() + ".sha1");
        File jar = dependency.getFile(baseDir, "jar");
        File jar1 = new File(jar.getPath() + ".sha1");
        Set<Dependency> downloaded = new HashSet<>();
        downloaded.add(dependency);
        if (pom.exists() && pom1.exists() && jar.exists() && jar1.exists()) {
            if (Objects.equals(readFileHash(pom), readFile(pom1)) && Objects.equals(readFileHash(jar), readFile(jar1))) {
                injectedDependencies.add(dependency);
                if (pom.exists()) {
                    downloaded.addAll(download(pom.toURI().toURL().openStream()));
                }
                return downloaded;
            }
        }
        pom.getParentFile().mkdirs();
        IOException e = null;
        for (Repository repo : repositories) {
            try {
                repo.downloadToFile(dependency, pom);
                repo.downloadToFile(dependency, new File(pom.getPath() + ".sha1"));
                try {
                    repo.downloadToFile(dependency, jar);
                    repo.downloadToFile(dependency, new File(jar.getPath() + ".sha1"));
                } catch (IOException exception) {
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document xml = builder.parse(pom);
                        try {
                            if (find("packaging", xml.getDocumentElement(), "pom").equals("jar")) {
                                throw exception;
                            }
                        } catch (ParseException ex) {
                            ex.addSuppressed(exception);
                            throw new IOException("Unable to find packaging information in pom.xml", ex);
                        }
                    } catch (ParserConfigurationException ex) {
                        ex.addSuppressed(exception);
                        throw new IOException("Unable to load pom.xml parser", ex);
                    } catch (SAXException ex) {
                        ex.addSuppressed(exception);
                        throw new IOException("Unable to parse pom.xml", ex);
                    } catch (IOException ex) {
                        if (ex != exception) {
                            ex.addSuppressed(exception);
                        }
                        throw ex;
                    }
                }
                if (pom.exists()) {
                    downloaded.addAll(download(pom.toURI().toURL().openStream()));
                }
                e = null;
                break;
            } catch (IOException ex) {
                if (e == null) {
                    e = new IOException(String.format("Unable to find download for %s", dependency));
                }
                e.addSuppressed(ex);
            }
        }
        if (e != null) {
            throw e;
        }
        return downloaded;
    }

    /**
     * Downloads a list of dependencies along with all of their dependencies and
     * stores them in the {@link DependencyDownloader#baseDir}.
     *
     * @param repositories The list of repositories to try to download from
     * @param dependencies The list of dependencies to download
     * @return The set of all dependencies that were downloaded
     * @throws IOException If an I/O error has occurred
     * @since 1.0.0
     */
    public Set<Dependency> download(List<Repository> repositories, List<Dependency> dependencies) throws IOException {
        createBaseDir();
        Set<Dependency> downloaded = new HashSet<>();
        for (Dependency dep : dependencies) {
            downloaded.addAll(download(repositories, dep));
        }
        injectClasspath(downloaded);
        return downloaded;
    }

    /**
     * Downloads all of the dependencies specified in the pom
     *
     * @param pom    The parsed pom file
     * @param scopes The scopes to download for
     * @return The set of all dependencies that were downloaded
     * @throws IOException If an I/O error has occurred
     * @since 1.0.0
     */
    public Set<Dependency> download(Document pom, DependencyScope... scopes) throws IOException {
        List<Dependency> dependencies = new ArrayList<>();
        Set<DependencyScope> scopeSet = new HashSet<>(Arrays.asList(scopes));
        NodeList nodes = pom.getDocumentElement().getChildNodes();
        List<Repository> repos = new ArrayList<>(repositories);
        if (repos.isEmpty()) {
            repos.add(new Repository());
        }
        try {
            for (int i = 0; i < nodes.getLength(); ++i) {
                Node node = nodes.item(i);
                if (node.getNodeName().equals("repositories")) {
                    nodes = ((Element) node).getElementsByTagName("repository");
                    for (i = 0; i < nodes.getLength(); ++i) {
                        Element e = (Element) nodes.item(i);
                        repos.add(new Repository(e));
                    }
                    break;
                }
            }
        } catch (ParseException ex) {
            throw new IOException("Unable to parse repositories", ex);
        }
        nodes = pom.getElementsByTagName("dependency");
        try {
            for (int i = 0; i < nodes.getLength(); ++i) {
                // ignore optional
                if (ignoreOptional && find("optional", (Element) nodes.item(i), "false").equals("true")) {
                    continue;
                }
                Dependency dep = new Dependency((Element) nodes.item(i));
                if (scopeSet.contains(dep.getScope())) {
                    dependencies.add(dep);
                }
            }
        } catch (ParseException ex) {
            throw new IOException("Unable to parse dependencies", ex);
        }
        return download(repos, dependencies);
    }

    /**
     * Downloads all of the dependencies specified in the pom for the default
     * scopes
     *
     * @param pom The parsed pom file
     * @return The set of all dependencies that were downloaded
     * @throws IOException If an I/O error has occurred
     * @see DependencyDownloader#dependencyScopes
     * @since 1.0.0
     */
    public Set<Dependency> download(Document pom) throws IOException {
        return download(pom, dependencyScopes);
    }

    /**
     * Downloads all of the dependencies specified in the pom
     *
     * @param pom    The stream containing the pom file
     * @param scopes The scopes to download for
     * @return The set of all dependencies that were downloaded
     * @throws IOException If an I/O error has occurred
     * @since 1.0.0
     */
    public Set<Dependency> download(InputStream pom, DependencyScope... scopes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document xml = builder.parse(pom);
            return download(xml, scopes);
        } catch (ParserConfigurationException ex) {
            throw new IOException("Unable to load pom.xml parser", ex);
        } catch (SAXException ex) {
            throw new IOException("Unable to parse pom.xml", ex);
        }
    }

    public void addRepository(Repository repository) {
        repositories.add(repository);
    }

    /**
     * Downloads all of the dependencies specified in the pom for the default
     * scopes
     *
     * @param pom The stream containing the pom file
     * @return The set of all dependencies that were downloaded
     * @throws IOException If an I/O error has occurred
     * @see DependencyDownloader#dependencyScopes
     * @since 1.0.0
     */
    public Set<Dependency> download(InputStream pom) throws IOException {
        return download(pom, dependencyScopes);
    }

    public File getBaseDir() {
        return baseDir;
    }

    public DependencyDownloader setBaseDir(File baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    public DependencyScope[] getDependencyScopes() {
        return dependencyScopes;
    }

    public DependencyDownloader setDependencyScopes(DependencyScope[] dependencyScopes) {
        this.dependencyScopes = dependencyScopes;
        return this;
    }

    public boolean isDebugMode() {
        return isDebugMode;
    }

    public DependencyDownloader setDebugMode(boolean debugMode) {
        isDebugMode = debugMode;
        return this;
    }

    public Set<Dependency> getInjectedDependencies() {
        return injectedDependencies;
    }

    public Set<Repository> getRepositories() {
        return repositories;
    }

    public boolean isIgnoreOptional() {
        return ignoreOptional;
    }

    public DependencyDownloader setIgnoreOptional(boolean ignoreOptional) {
        this.ignoreOptional = ignoreOptional;
        return this;
    }

    @NotNull
    private String readFileHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("sha-1");
            try (InputStream inputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int total;
                while ((total = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, total);
                }
            }
            return getHash(digest);
        } catch (IOException | NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }
        return "null (" + UUID.randomUUID().toString() + ")";
    }

    private String getHash(MessageDigest digest) {
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    @NotNull
    private String readFile(File file) {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            return readFully(fileInputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "null (" + UUID.randomUUID().toString() + ")";
    }

    private String readFully(InputStream inputStream, Charset charset) throws IOException {
        return new String(readFully(inputStream), charset);
    }

    private byte[] readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int len;
        while ((len = inputStream.read(buf)) > 0) {
            stream.write(buf, 0, len);
        }
        return stream.toByteArray();
    }
}
