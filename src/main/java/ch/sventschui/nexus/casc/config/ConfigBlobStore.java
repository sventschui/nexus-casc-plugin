package ch.sventschui.nexus.casc.config;

import org.sonatype.nexus.blobstore.file.FileBlobStore;

import java.util.Map;

public class ConfigBlobStore {
    private String name;
    private Map<String, Map<String, Object>> attributes;
    private String type = FileBlobStore.TYPE;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Map<String, Object>> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Map<String, Object>> attributes) {
        this.attributes = attributes;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
