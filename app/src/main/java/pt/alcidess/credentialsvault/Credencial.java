package pt.alcidess.credentialsvault;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class Credencial implements Parcelable {
    private long id;
    private String nome;
    private String username;
    private String password;
    private int passwordLength;
    private boolean useUppercase;
    private boolean useLowercase;
    private boolean useDigits;
    private String specialChars;

    private long lastUpdated = System.currentTimeMillis(); // valor por omissão

    public Credencial() {
        this.specialChars = "";
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // para testes
    public static Credencial createDefault(Context context) {
        Credencial c = new Credencial();
        c.setPasswordLength(AppConfig.getDefaultPasswordLength(context));
        c.setUseUppercase(AppConfig.getDefaultUseUppercase(context));
        c.setUseLowercase(AppConfig.getDefaultUseLowercase(context));
        c.setUseDigits(AppConfig.getDefaultUseDigits(context));
        c.setSpecialChars(AppConfig.getDefaultSpecialChars(context));
        return c;
    }

    // Construtor completo
    public Credencial(long id, String nome, String username, String password) {
        this.id = id;
        this.nome = nome;
        this.username = username;
        this.password = password;
    }

    // Usado ao carregar da BD
    public Credencial(long id, String nome, String username, String password, long lastUpdated) {
        this.id = id;
        this.nome = nome;
        this.username = username;
        this.password = password;
        this.lastUpdated = lastUpdated;
    }

    // Getters & Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getPasswordLength() { return passwordLength; }
    public void setPasswordLength(int passwordLength) { this.passwordLength = passwordLength; }

    public boolean isUseUppercase() { return useUppercase; }
    public void setUseUppercase(boolean useUppercase) { this.useUppercase = useUppercase; }

    public boolean isUseLowercase() { return useLowercase; }
    public void setUseLowercase(boolean useLowercase) { this.useLowercase = useLowercase; }

    public boolean isUseDigits() { return useDigits; }
    public void setUseDigits(boolean useDigits) { this.useDigits = useDigits; }

    public String getSpecialChars() { return specialChars; }
    public void setSpecialChars(String specialChars) {
        this.specialChars = (specialChars != null) ? specialChars : "";
    }

    // Getter para exportação JSON (com opção de mascarar)
    public String getPasswordForExport(boolean reveal) {
        return reveal ? password : "••••••••";
    }

    public String getLastUpdatedFormatted() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(lastUpdated));
    }

    public void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }
    // ========== Parcelable ==========
    protected Credencial(Parcel in) {
        id = in.readLong();
        nome = in.readString();
        username = in.readString();
        password = in.readString();
        passwordLength = in.readInt();
        useUppercase = in.readByte() != 0;
        useLowercase = in.readByte() != 0;
        useDigits = in.readByte() != 0;
        specialChars = in.readString();
        lastUpdated = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(nome);
        dest.writeString(username);
        dest.writeString(password);
        dest.writeInt(passwordLength);
        dest.writeByte((byte) (useUppercase ? 1 : 0));
        dest.writeByte((byte) (useLowercase ? 1 : 0));
        dest.writeByte((byte) (useDigits ? 1 : 0));
        dest.writeString(specialChars);
        dest.writeLong(lastUpdated);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Credencial> CREATOR = new Creator<Credencial>() {
        @Override
        public Credencial createFromParcel(Parcel in) {
            return new Credencial(in);
        }

        @Override
        public Credencial[] newArray(int size) {
            return new Credencial[size];
        }
    };

    // Método para converter para JsonObject
    public JsonObject toJson(boolean revealPassword) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("nome", nome);
        obj.addProperty("username", username);
        obj.addProperty("password", getPasswordForExport(revealPassword));
        obj.addProperty("password_length", passwordLength);
        obj.addProperty("use_uppercase", useUppercase);
        obj.addProperty("use_lowercase", useLowercase);
        obj.addProperty("use_digits", useDigits);
        obj.addProperty("special_chars", specialChars);
        return obj;
    }

    // Estático: converter lista para JsonArray
    public static JsonArray toJsonArray(List<Credencial> credenciais, boolean revealPassword) {
        JsonArray array = new JsonArray();
        for (Credencial c : credenciais) {
            array.add(c.toJson(revealPassword));
        }
        return array;
    }
}