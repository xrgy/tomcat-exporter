package com.gy.tomcat.collector;

import com.gy.tomcat.httpserver.WebServer;
import com.gy.tomcat.model.DbData;
import io.prometheus.client.Collector;
import org.yaml.snakeyaml.Yaml;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * Created by gy on 2018/5/22.
 */
public class JmxCollector extends Collector{
    public JmxCollector(String target){
        setConnect(target);
    }
    private static class Rule {
        Pattern pattern;
        String name;
        String value;
        Double valueFactor = 1.0;
        String help;
        boolean attrNameSnakeCase;
        Type type = Type.UNTYPED;
        ArrayList<String> labelNames;
        ArrayList<String> labelValues;
    }

    private static class Config {
        Integer startDelaySeconds = 0;
        String jmxUrl = "";
        String username = "";
        String password = "";
        boolean ssl = false;
        boolean lowercaseOutputName;
        boolean lowercaseOutputLabelNames;
        List<ObjectName> whitelistObjectNames = new ArrayList<ObjectName>();
        List<ObjectName> blacklistObjectNames = new ArrayList<ObjectName>();
        List<Rule> rules = new ArrayList<Rule>();
    }

    private Config config;
    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("([a-z0-9])([A-Z])");
    private final Pattern unsafeChars = Pattern.compile("[^a-zA-Z0-9:_]");
    private final Pattern multipleUnderscores = Pattern.compile("__+");

    private void setConnect(String target) {
        DbData dbData = WebServer.getDbData(target);
        try {
            config = loadConfig(dbData.getIp(),dbData.getPort(),dbData.getUserName(),dbData.getPassword());
        }catch (Exception e){
            config = null;
        }
    }

    private Config loadConfig(String ip, String port, String userName, String password) throws MalformedObjectNameException {
        Config cfg = new Config();
        if (ip.isEmpty() || port.isEmpty()){
            cfg.jmxUrl = "";
            cfg.username = "";
            cfg.password = "";
        }else {
            cfg.jmxUrl = "service:jmx:rmi:///jndi/rmi://" + ip +":" + port + "/jmxrmi";
            cfg.username = userName;
            cfg.password = password;
        }
        cfg.ssl = false;
        cfg.whitelistObjectNames.add(null);
        cfg.rules.add(new Rule());
        File configFile = new File("C:/Users/gy/IdeaProjects/tomcat-exporter/src/main/java/com/gy/tomcat/config.yaml");
//        File configFile =  new File("/config.yaml");
        Map<String, Object> yamlConfig = null;
        try {
            yamlConfig = (Map<String, Object>) new Yaml().load(new FileReader(configFile));
        } catch (FileNotFoundException e) {
            System.out.println("/config.yaml FileNotFoundException:"+e.getMessage());
            e.printStackTrace();
        }
        if (yamlConfig.containsKey("whitelistObjectNames")){
            System.out.println("has read /config.yaml");
            cfg.whitelistObjectNames.clear();
            List<Object> names = (List<Object>) yamlConfig.get("whitelistObjectNames");
            for (Object name : names){
                cfg.whitelistObjectNames.add(new ObjectName((String) name));
            }
        }else {
            cfg.whitelistObjectNames.add(null);
        }
        return cfg;
    }

    static String toSnakeAndLowerCase(String attrName) {
        if (attrName == null || attrName.isEmpty()) {
            return attrName;
        }
        char firstChar = attrName.subSequence(0, 1).charAt(0);
        boolean prevCharIsUpperCaseOrUnderscore = Character.isUpperCase(firstChar) || firstChar == '_';
        StringBuilder resultBuilder = new StringBuilder(attrName.length()).append(Character.toLowerCase(firstChar));
        for (char attrChar : attrName.substring(1).toCharArray()) {
            boolean charIsUpperCase = Character.isUpperCase(attrChar);
            if (!prevCharIsUpperCaseOrUnderscore && charIsUpperCase) {
                resultBuilder.append("_");
            }
            resultBuilder.append(Character.toLowerCase(attrChar));
            prevCharIsUpperCaseOrUnderscore = charIsUpperCase || attrChar == '_';
        }
        return resultBuilder.toString();
    }

    /**
     * Change invalid chars to underscore, and merge underscores.
     * @param
     * @return
     */
//    static String safeName(String name) {
//        if (name == null) {
//            return null;
//        }
//        boolean prevCharIsUnderscore = false;
//        StringBuilder safeNameBuilder = new StringBuilder(name.length());
//        for (char nameChar : name.toCharArray()) {
//            boolean isUnsafeChar = !(Character.isLetterOrDigit(nameChar) || nameChar == ':' || nameChar == '_');
//            if ((isUnsafeChar || nameChar == '_')) {
//                if (prevCharIsUnderscore) {
//                    continue;
//                } else {
//                    safeNameBuilder.append("_");
//                    prevCharIsUnderscore = true;
//                }
//            } else {
//                safeNameBuilder.append(nameChar);
//                prevCharIsUnderscore = false;
//            }
//        }
//
//        return safeNameBuilder.toString();
//    }

     private String safeName(String s){
         return multipleUnderscores.matcher(unsafeChars.matcher(s).replaceAll("_")).replaceAll("_");

     }

    class Receiver implements JmxScraper.MBeanReceiver {
        Map<String, MetricFamilySamples> metricFamilySamplesMap =
                new HashMap<>();

        private static final char SEP = '_';



        // [] and () are special in regexes, so swtich to <>.
        private String angleBrackets(String s) {
            return "<" + s.substring(1, s.length() - 1) + ">";
        }

        void addSample(MetricFamilySamples.Sample sample, Type type, String help) {
            MetricFamilySamples mfs = metricFamilySamplesMap.get(sample.name);
            if (mfs == null) {
                // JmxScraper.MBeanReceiver is only called from one thread,
                // so there's no race here.
                mfs = new MetricFamilySamples(sample.name, type, help, new ArrayList<MetricFamilySamples.Sample>());
                metricFamilySamplesMap.put(sample.name, mfs);
            }
            mfs.samples.add(sample);
        }

        private void defaultExport(
                String domain,
                LinkedHashMap<String, String> beanProperties,
                LinkedList<String> attrKeys,
                String attrName,
                String help,
                Object value,
                Type type) {
            StringBuilder name = new StringBuilder();
            name.append(domain);
            if (beanProperties.size() > 0) {
                name.append(SEP);
                name.append(beanProperties.values().iterator().next());
            }
            for (String k : attrKeys) {
                name.append(SEP);
                name.append(k);
            }
            name.append(SEP);
            name.append(attrName);
            String fullname = safeName(name.toString());

            if (config.lowercaseOutputName) {
                fullname = fullname.toLowerCase();
            }

            List<String> labelNames = new ArrayList<String>();
            List<String> labelValues = new ArrayList<String>();
            if (beanProperties.size() > 1) {
                Iterator<Map.Entry<String, String>> iter = beanProperties.entrySet().iterator();
                // Skip the first one, it's been used in the name.
                iter.next();
                while (iter.hasNext()) {
                    Map.Entry<String, String> entry = iter.next();
                    String labelName = safeName(entry.getKey());
                    if (config.lowercaseOutputLabelNames) {
                        labelName = labelName.toLowerCase();
                    }
                    labelNames.add(labelName);
                    labelValues.add(entry.getValue());
                }
            }

            addSample(new MetricFamilySamples.Sample(fullname, labelNames, labelValues, ((Number)value).doubleValue()),
                    type, help);
        }

        @Override
        public void recordBean(
                String domain,
                LinkedHashMap<String, String> beanProperties,
                LinkedList<String> attrKeys,
                String attrName,
                String attrType,
                String attrDescription,
                Object beanValue) {

            String beanName = domain + angleBrackets(beanProperties.toString()) + angleBrackets(attrKeys.toString());
            // attrDescription tends not to be useful, so give the fully qualified name too.
            String help = attrDescription + " (" + beanName + attrName + ")";
            String attrNameSnakeCase = toSnakeAndLowerCase(attrName);

            for (Rule rule : config.rules) {
                Matcher matcher = null;
                String matchName = beanName + (rule.attrNameSnakeCase ? attrNameSnakeCase : attrName);
                if (rule.pattern != null) {
                    matcher = rule.pattern.matcher(matchName + ": " + beanValue);
                    if (!matcher.matches()) {
                        continue;
                    }
                }

                Number value;
                if (rule.value != null && !rule.value.isEmpty()) {
                    String val = matcher.replaceAll(rule.value);

                    try {
                        beanValue = Double.valueOf(val);
                    } catch (NumberFormatException e) {
//                        LOGGER.fine("Unable to parse configured value '" + val + "' to number for bean: " + beanName + attrName + ": " + beanValue);
                        return;
                    }
                }
                if (beanValue instanceof Number) {
                    value = ((Number)beanValue).doubleValue() * rule.valueFactor;
                } else if (beanValue instanceof Boolean) {
                    value = (Boolean)beanValue ? 1 : 0;
                } else {
//                    LOGGER.fine("Ignoring unsupported bean: " + beanName + attrName + ": " + beanValue);
                    return;
                }

                // If there's no name provided, use default export format.
                if (rule.name == null) {
                    defaultExport(domain, beanProperties, attrKeys, rule.attrNameSnakeCase ? attrNameSnakeCase : attrName, help, value, rule.type);
                    return;
                }

                // Matcher is set below here due to validation in the constructor.
                String name = safeName(matcher.replaceAll(rule.name));
                if (name.isEmpty()) {
                    return;
                }
                if (config.lowercaseOutputName) {
                    name = name.toLowerCase();
                }

                // Set the help.
                if (rule.help != null) {
                    help = matcher.replaceAll(rule.help);
                }

                // Set the labels.
                ArrayList<String> labelNames = new ArrayList<String>();
                ArrayList<String> labelValues = new ArrayList<String>();
                if (rule.labelNames != null) {
                    for (int i = 0; i < rule.labelNames.size(); i++) {
                        final String unsafeLabelName = rule.labelNames.get(i);
                        final String labelValReplacement = rule.labelValues.get(i);
                        try {
                            String labelName = safeName(matcher.replaceAll(unsafeLabelName));
                            String labelValue = matcher.replaceAll(labelValReplacement);
                            if (config.lowercaseOutputLabelNames) {
                                labelName = labelName.toLowerCase();
                            }
                            if (!labelName.isEmpty() && !labelValue.isEmpty()) {
                                labelNames.add(labelName);
                                labelValues.add(labelValue);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(
                                    format("Matcher '%s' unable to use: '%s' value: '%s'", matcher, unsafeLabelName, labelValReplacement), e);
                        }
                    }
                }

                // Add to samples.
//                LOGGER.fine("add metric sample: " + name + " " + labelNames + " " + labelValues + " " + value.doubleValue());
                addSample(new MetricFamilySamples.Sample(name, labelNames, labelValues, value.doubleValue()), rule.type, help);
                return;
            }
        }
    }


    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfsList = new ArrayList<>();
        List<MetricFamilySamples.Sample> samples = new ArrayList<>();
        if (null == config){
            setMonitorStatus(mfsList);
            return mfsList;
        }
        Receiver receiver = new Receiver();
        JmxScraper scraper = new JmxScraper(config.jmxUrl,config.username,config.password,config.ssl,config.whitelistObjectNames,config.blacklistObjectNames,receiver);
        Long start = System.nanoTime();
        double error = 0;
        try {
            scraper.doScrape();
        }catch (Exception e) {
            error = 1;
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
//            LOGGER.severe("JMX scrape failed: " + sw.toString());
        }
        mfsList.addAll(receiver.metricFamilySamplesMap.values());
        samples.add(new MetricFamilySamples.Sample(
                "jmx_scrape_duration_seconds", new ArrayList<String>(), new ArrayList<String>(), (System.nanoTime() - start) / 1.0E9));
        mfsList.add(new MetricFamilySamples("jmx_scrape_duration_seconds", Type.GAUGE, "Time this JMX scrape took, in seconds.", samples));

        samples = new ArrayList<MetricFamilySamples.Sample>();
        samples.add(new MetricFamilySamples.Sample(
                "jmx_scrape_error", new ArrayList<String>(), new ArrayList<String>(), error));
        mfsList.add(new MetricFamilySamples("jmx_scrape_error", Type.GAUGE, "Non-zero if this scrape failed.", samples));
        return mfsList;
    }

    private void setMonitorStatus(List<MetricFamilySamples> samples) {
        List<MetricFamilySamples.Sample> ms = new ArrayList<>();
        List<String> labelName = new ArrayList<>();
        List<String> labelValue = new ArrayList<>();
        ms.add(new MetricFamilySamples.Sample("tomcat_monitorstatus",labelName,labelName,0));
        samples.add(new MetricFamilySamples("tomcat_monitorstatus",Type.GAUGE,"basic monitorstatus",ms));

    }
}
