package com.store.didcall;

import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** TV 왼쪽 칸에 띄울 이미지 보관소 (앱 내부 저장소) */
final class ImageStore {
    static final int MAX_IMAGES = 30;
    private static final String NAME_RE = "[0-9]{1,20}\\.(jpg|png|webp)";

    private final File dir;

    ImageStore(File dir) {
        this.dir = dir;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    synchronized List<File> list() {
        File[] files = dir.listFiles((d, name) -> name.matches(NAME_RE));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        return new ArrayList<>(Arrays.asList(files));
    }

    synchronized int count() {
        return list().size();
    }

    /** @return 오류 메시지, 성공이면 null */
    synchronized String add(byte[] data) {
        if (data == null || data.length < 16) return "이미지 데이터가 없습니다";
        if (count() >= MAX_IMAGES) return "이미지는 최대 " + MAX_IMAGES + "장까지 등록할 수 있습니다";
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return "이미지 파일이 아닙니다";

        String ext = "jpg";
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P') ext = "png";
        else if (data[0] == 'R' && data[1] == 'I' && data[8] == 'W') ext = "webp";

        long id = System.currentTimeMillis();
        File f;
        while ((f = new File(dir, id + "." + ext)).exists()) id++;
        File tmp = new File(dir, id + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "저장 공간이 부족합니다";
        }
        return tmp.renameTo(f) ? null : "저장에 실패했습니다";
    }

    /** 이름이 올바르고 실제로 있는 파일만 반환 (경로 조작 방지) */
    synchronized File get(String name) {
        if (name == null || !name.matches(NAME_RE)) return null;
        File f = new File(dir, name);
        return f.isFile() ? f : null;
    }

    synchronized boolean delete(String name) {
        File f = get(name);
        return f != null && f.delete();
    }

    synchronized void clear() {
        for (File f : list()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
