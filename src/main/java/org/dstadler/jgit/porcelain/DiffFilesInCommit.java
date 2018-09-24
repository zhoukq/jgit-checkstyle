//ssda
package org.dstadler.jgit.porcelain;

/*
   Copyright 2013, 2014 Dominik Stadler

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Simple snippet which shows how to retrieve the diffs between two commits
 */
public class DiffFilesInCommit {

    public static Repository openkRepository() throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        return builder
            .readEnvironment() // scan environment GIT_* variables
            .findGitDir() // scan up the file system tree
            .build();
    }

    public static void main(String[] args) throws IOException, GitAPIException {
        try (Repository repository = openkRepository()) {
            try (Git git = new Git(repository)) {
                RevCommit mergeBase = getMergeBase(repository, "refs/heads/master");

                listDiff(repository, git, mergeBase.getName(), repository.resolve("HEAD").getName());
                // compare older commit with the newer one, showing an addition
                // and 2 changes
//                listDiff(repository, git,
//                        "3cc51d5cfd1dc3e890f9d6ded4698cb0d22e650e",
//                        "19536fe5765ee79489265927a97cb0e19bb93e70");
//
//                // also the diffing the reverse works and now shows a delete
//                // instead of the added file
//                listDiff(repository, git,
//                        "19536fe5765ee79489265927a97cb0e19bb93e70",
//                        "3cc51d5cfd1dc3e890f9d6ded4698cb0d22e650e");
//
//                // to compare against the "previous" commit, you can use
//                // the caret-notation
//                listDiff(repository, git,
//                        "19536fe5765ee79489265927a97cb0e19bb93e70^",
//                        "19536fe5765ee79489265927a97cb0e19bb93e70");
            }
        }
    }

    private static RevCommit getMergeBase(Repository repository, String branch) {
        RevWalk walk = new RevWalk(repository);
        RevCommit result = null;
        try {
            walk.markStart(walk.parseCommit(repository.resolve("HEAD")));
            walk.markStart(walk.parseCommit(repository.resolve(branch)));
            walk.setRevFilter(RevFilter.MERGE_BASE);
            result = walk.next();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    private static void listDiff(Repository repository, Git git, String oldCommit, String newCommit) throws GitAPIException, IOException {
        final List<DiffEntry> diffs = git.diff()
            .setOldTree(prepareTreeParser(repository, oldCommit))
            .setNewTree(prepareTreeParser(repository, newCommit))
            .call();
        System.out.println("Found: " + diffs.size() + " differences");
        OutputStream outputStream = DisabledOutputStream.INSTANCE;
        try (DiffFormatter formatter = new DiffFormatter(outputStream)) {
            formatter.setRepository(git.getRepository());
            Map<String, List<Pair<String, String>>> resultMap = new LinkedHashMap<>();
            for (DiffEntry diff : diffs) {
                System.out.println("Diff: " + diff.getChangeType() + ": " +
                    (diff.getOldPath().equals(diff.getNewPath()) ? diff.getNewPath() : diff.getOldPath() + " -> " + diff.getNewPath()));
                int fileLength = getFileLines(diff.getNewPath());
                String className = Stream.of(diff.getNewPath().split("/")).reduce((first, second) -> second).orElse("");
                System.out.println("lines: " + fileLength);
                FileHeader fileHeader = formatter.toFileHeader(diff);
                EditList edits = fileHeader.toEditList();
                edits.add(new Edit(0, -1, 0, -1));
                edits.add(new Edit(fileLength, fileLength));
                edits.sort((f, s) -> f.getBeginB() - s.getBeginB() == 0 ? f.getEndB() - s.getEndB() : f.getBeginB() - s.getBeginB());
                System.out.println(edits);
                List<Pair<String, String>> pairList = IntStream.range(0, edits.size())
                    .filter(i -> edits.get(i).getType() != Type.DELETE)
                    .mapToObj(i -> {
                        if (i == 0) {
                            return new Pair<>(String.valueOf(edits.get(i).getBeginB()), String.valueOf(edits.get(i + 1).getBeginB()));
                        } else if (i == edits.size() - 1) {
                            return new Pair<>(String.valueOf(edits.get(i - 1).getEndB() + 1), String.valueOf(edits.get(i).getBeginB()));
                        } else {
                            return new Pair<>(String.valueOf(edits.get(i).getEndB() + 1), String.valueOf(edits.get(i + 1).getBeginB()));
                        }
                    })
                    .filter(e -> Integer.valueOf(e.getValue()) > Integer.valueOf(e.getKey()))
                    .distinct()
                    .sorted(Comparator.comparingInt(f -> Integer.valueOf(f.getKey())))
                    .collect(Collectors.toList());
                resultMap.put(className, pairList);
            }
            parseXML(resultMap);
        }


    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        // from the commit we can build the tree which allows us to construct the TreeParser
        //noinspection Duplicates
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }

    private static int getFileLines(String file) {
        int lines = 0;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            while (reader.readLine() != null) {
                lines++;
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    private static String getLinesValue(List<Pair<String, String>> lineList) {
        return lineList.stream()
            .map(e -> e.getKey() + "-" + e.getValue())
            .collect (Collectors.joining (","));
    }

    private static void parseXML(Map<String, List<Pair<String, String>>> linesMap) {
        try {
            File inputFile = new File("config/checkstyle/suppressions.xml");
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(inputFile);
            NodeList suppressList = doc.getElementsByTagName("suppress");

            //edit file
            IntStream.range(0, suppressList.getLength())
                .filter(i -> linesMap.containsKey(suppressList.item(i).getAttributes().getNamedItem("files").getNodeValue()))
                .forEach(i -> ((Element) suppressList.item(i))
                    .setAttribute("lines", getLinesValue(linesMap.get(suppressList.item(i).getAttributes().getNamedItem("files").getNodeValue()))));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");

            DOMImplementation impl = doc.getImplementation();
            DocumentType doctype = impl.createDocumentType("doctype",
                "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN",
                "https://checkstyle.org/dtds/suppressions_1_2.dtd");

            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctype.getPublicId());
            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doctype.getSystemId());
            DOMSource source = new DOMSource(doc);
            System.out.println("-----------Modified File-----------");
            StreamResult fileResult = new StreamResult(new File("config/checkstyle/suppressions.xml"));
            transformer.transform(source, fileResult);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
