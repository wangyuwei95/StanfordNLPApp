package team.intelligenthealthcare.keywordsextraction;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Corpus {
    private final StanfordCoreNLP corenlp;
    private final Dict dict;
    private final String defaultTag;



    public Corpus(String segmentationPropertyFileName, Dict dict, String defaultTag) {
        corenlp = new StanfordCoreNLP(segmentationPropertyFileName);
        this.dict = dict;
        this.defaultTag = defaultTag;
    }

    private class Worker implements Runnable {
        Worker(String text, int i, List<List<List<List<String>>>> acc) {
            this.text = text;
            this.i = i;
            this.acc = acc;
        }

        @Override
        public void run() {
            if(text.isEmpty()) return;
            //split text to sentences
            List<String> sentences = new ArrayList<>();
            Annotation document = new Annotation(text);
            corenlp.annotate(document);
            for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
                StringBuilder sb = new StringBuilder();
                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    sb.append(token.get(CoreAnnotations.TextAnnotation.class));
                }
                sentences.add(sb.toString());
            }
            //tag each sentence
            List<List<List<String>>> res = new ArrayList<>();
            for(String sentence : sentences)
            {
                //tag with ahocorasick
                List<List<String>> old = dict.ahocorasickParse(sentence);
                List<String> oldWords = old.get(0), oldTags = old.get(1);
                List<String> words = new ArrayList<>(), tags = new ArrayList<>();
                for(int i = 0; i < oldWords.size(); i++)
                {
                    //split bigwords with default tag via stanfordNLP
                    if(oldTags.get(i).equals(defaultTag))
                    {
                        Annotation curDocument = new Annotation(oldWords.get(i));
                        corenlp.annotate(curDocument);
                        for (CoreMap a : curDocument.get(CoreAnnotations.SentencesAnnotation.class)) {
                            for (CoreLabel b : a.get(CoreAnnotations.TokensAnnotation.class)) {
                                words.add(b.get(CoreAnnotations.TextAnnotation.class));
                                tags.add(defaultTag);
                            }
                        }
                    } else {
                        words.add(oldWords.get(i));
                        tags.add(oldTags.get(i));
                    }
                }
                res.add(new ArrayList<List<String>>(){{add(words);add(tags);}});
            }
            synchronized (acc) {
                acc.set(i, res);
            }
        }

        //tag corpus with words in dicts, if not find ,tag with default tag.
//        public void parse(Annotation document) {
//            List<String> words, tags;
//            List<List<List<String>>> curResult = new ArrayList<>();
//            for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
//                words = new ArrayList<>();
//                tags = new ArrayList<>();
//                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
//                    //get each word in a sentence
//                    words.add(token.get(CoreAnnotations.TextAnnotation.class));
//                    //tag it with default tag
//                    tags.add(defaultTag);
//                }
//                //tag by matching consecutive words with dicts
//                StringBuilder sb = new StringBuilder();
//                OutLabel:
//                for (int i = 0; i < words.size(); i++) {
//                    sb.delete(0, sb.length());
//                    for (int j = i; j < words.size() && j < i + 20; j++) {
//                        sb.append(words.get(j));
//                        //if the word has already been tagged, skip it.
//                        if (!tags.get(j).equals(defaultTag))
//                            break;
//                        //now sb contains consecutive words "words[i]+words[i+1]...words[j]"
//                        //If that big word is in a dict, tag it
//                        for (Map.Entry<String, Set<String>> tagAndDict : dict.getDict().entrySet()) {
//                            String tag = tagAndDict.getKey();
//                            Set<String> dict = tagAndDict.getValue();
//                            if (dict.contains(sb.toString())) {
//                                for (int k = i; k <= j; k++)
//                                    tags.set(k, tag);
//                                //Since we have already tagged the big word, skip.
//                                i = j - 1;
//                                continue OutLabel;
//                            }
//                        }
//                    }
//                }
//                curResult.add(new ArrayList<>());
//                curResult.get(curResult.size()-1).add(words);
//                curResult.get(curResult.size()-1).add(tags);
//            }
//            synchronized (acc) {
//                acc.set(i, curResult);
//            }
//        }
        private String text;
        private int i;
        private List<List<List<List<String>>>> acc;
    }

    //run stanfordCoreNLP
    //in ith sentence and jth word, res[i][0][j]:word   res[i][1][j]:tag
    public List<List<List<String>>> tagFile(String fileName, int maxLen) throws IOException {
        List<String> unmarkedCorpus = MyUtils.readFileAsMultipleLines(fileName, maxLen);
        return tagStrings(unmarkedCorpus);
    }
    public List<List<List<String>>> tagString(String str) throws IOException {
        List<String> unmarkedCorpus = new ArrayList<>();
        unmarkedCorpus.add(str);
        return tagStrings(unmarkedCorpus);
    }
    public List<List<List<String>>> tagStrings(List<String> unmarkedCorpus) throws IOException {
        int i = 0;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<List<List<List<String>>>> acc = new ArrayList<>();
        for (int j = 0; j < unmarkedCorpus.size(); j++) {
            acc.add(new ArrayList<>());
        }
        for (String text : unmarkedCorpus) {
            executor.execute(new Worker(text, i, acc));
            i++;
        }
        executor.shutdown();
        try {
            while(!executor.awaitTermination(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //now combine the result
        List<List<List<String>>> result = new ArrayList<>();
        for(List<List<List<String>>> iter : acc)
            result.addAll(iter);
        return result;
    }



//    //now we have analyzed the small file, write the results.
//    public void writeMarkedCorpusToFile(String markedCorpusFileName) throws IOException {
//        //System.out.println("write marked corpus "+outputFileName);
//        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(Thread.currentThread().getContextClassLoader().getResource("").getFile() + markedCorpusFileName))));
//
//        for (int i = 0; i < Math.min(allWords.size(), allTags.size()); i++) {
//            List<String> wordsInASentence = allWords.get(i);
//            List<String> tagsInASentence = allTags.get(i);
//            for (int j = 0; j < Math.min(wordsInASentence.size(), tagsInASentence.size()); j++) {
//                writer.write(wordsInASentence.get(j) + "\t" + tagsInASentence.get(j) + "\r\n");
//            }
//            writer.write("\r\n");
//        }
//
//        writer.flush();
//        writer.close();
//    }
}
