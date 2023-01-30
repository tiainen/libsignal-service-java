/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package tokhttp3;

/**
 *
 * @author johan
 */
public class MultipartBody {

    public static final MediaType FORM = MediaType.parse("multipart/form-data");
    
    public static class Builder {

        private MediaType mediaType;
        
        public Builder() {
        }

        public Builder setType(MediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public Builder addFormDataPart(String name, String val) {
            throw new UnsupportedOperationException("Not supported yet."); 
        }
        
        public Builder addFormDataPart(String name, String filename, RequestBody body) {
            throw new UnsupportedOperationException("Not supported yet."); 
        }

        public RequestBody build() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    }
    
}
