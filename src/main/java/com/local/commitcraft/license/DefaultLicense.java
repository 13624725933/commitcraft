package com.local.commitcraft.license;

public final class DefaultLicense {
    private static final String ACTIVATION_CODE = "CC1."
            + "dmVyc2lvbj0xCnByb2R1Y3Q9Q29tbWl0Q3JhZnQKbGljZW5zZUlkPTdjODg4NmFlLTZjNjMtNGU3Yi1hM2YwLWQyYzNmMGYzZTlmMQpidXllcj1Db21taXRDcmFmdCtBdXRvK0xpY2Vuc2UKbWFjaGluZT0qCmlzc3VlZEF0PTIwMjYtMDctMDkKZXhwaXJlc0F0PW5ldmVyCg"
            + "."
            + "PAjZ7g4EV_pjglMc27cU8F36xpjrw0ESUhetKXRUXQpz3PBwNm3vtxrIb0nTnQ_kej-ObrueDtrPdB3LvamyBw";

    private DefaultLicense() {
    }

    public static String activationCode() {
        return ACTIVATION_CODE;
    }
}
