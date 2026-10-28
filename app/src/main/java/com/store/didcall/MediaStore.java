package com.store.didcall;

import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TV 왼쪽 칸에 띄울 이미지·동영상 보관소 (호출기 폰 내부 저장소).
 * 순서는 order.txt 에 저장하며, 목록에 없는 파일은 이름(= 추가 시각) 순으로 뒤에 붙음.
 */
final class MediaStore {
    static final int MAX_ITEMS = 30;
    static final long MAX_IMAGE = 20L * 1024 * 1024;
    static final long MAX_VIDEO = 300L * 1024 * 1024;
    private static final String NAME_RE = "[0-9]{1,20}\\.(jpg|png|webp|mp4|webm)";

    private final File dir;
    private final File orderFile;
    private long version = System.currentTimeMillis(); // 목록이 바뀔 때마다 증가 → 화면이 다시 읽음

    MediaStore(File dir) {
        this.dir = dir;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        this.orderFile = new File(dir, "order.txt");
        cleanupTmp();
    }

    static boolean isVideo(String name) {
        return name.endsWith(".mp4") || name.endsWith(".webm");
    }

    static String contentType(String name) {
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        return "image/jpeg";
    }

    synchronized long version() {
        return version;
    }

    synchronized List<File> list() {
        File[] files = dir.listFiles((d, name) -> name.matches(NAME_RE));
        List<File> all = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        // List.sort 는 안드로이드 7.0 이상에만 있으므로 Collections.sort 사용 (5.0 지원)
        java.util.Collections.sort(all, (a, b) -> a.getName().compareTo(b.getName()));
        List<File> out = new ArrayList<>();
        for (String n : readOrder()) {
            for (File f : all) {
                if (f.getName().equals(n)) { out.add(f); break; }
            }
        }
        for (File f : all) if (!out.contains(f)) out.add(f);
        return out;
    }

    synchronized int count() {
        return list().size();
    }

    synchronized int videoCount() {
        int n = 0;
        for (File f : list()) if (isVideo(f.getName())) n++;
        return n;
    }

    /**
     * 업로드 본문을 파일로 바로 저장 (큰 동영상도 메모리에 올리지 않음).
     * @return 오류 메시지, 성공이면 null
     */
    String addFromStream(InputStream in, long len) throws IOException {
        if (len <= 16) return "파일 내용이 없습니다";
        if (len > MAX_VIDEO) return "동영상은 300MB 이하만 올릴 수 있습니다";
        synchronized (this) {
            if (count() >= MAX_ITEMS) return "이미지·동영상은 합쳐서 최대 " + MAX_ITEMS + "개까지 등록할 수 있습니다";
            if (dir.getUsableSpace() < len + 200L * 1024 * 1024) return "호출기 폰의 저장 공간이 부족합니다";
        }
        File tmp = new File(dir, System.nanoTime() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            long left = len;
            while (left > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (r < 0) throw new IOException("unexpected end");
                out.write(buf, 0, r);
                left -= r;
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        String ext = sniff(tmp);
        if (ext == null) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "이미지(jpg·png·webp) 또는 동영상(mp4·webm) 파일만 올릴 수 있습니다";
        }
        if (!isVideo("x." + ext) && len > MAX_IMAGE) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "이미지는 20MB 이하만 올릴 수 있습니다";
        }
        synchronized (this) {
            long id = System.currentTimeMillis();
            File f;
            while ((f = new File(dir, id + "." + ext)).exists()) id++;
            if (!tmp.renameTo(f)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return "저장에 실패했습니다";
            }
            List<String> order = names();
            writeOrder(order);
            version++;
        }
        return null;
    }

    /** 파일 앞부분으로 종류 판별 */
    private static String sniff(File f) {
        byte[] h = new byte[16];
        int n;
        try (FileInputStream in = new FileInputStream(f)) {
            n = in.read(h);
        } catch (IOException e) {
            return null;
        }
        if (n < 12) return null;
        // 동영상
        if (h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') return "mp4";
        if ((h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3) return "webm";
        // 이미지 (실제로 열리는지 확인)
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P') return "png";
        if (h[0] == 'R' && h[1] == 'I' && h[8] == 'W') return "webp";
        return "jpg";
    }

    /** 이름이 올바르고 실제로 있는 파일만 반환 (경로 조작 방지) */
    synchronized File get(String name) {
        if (name == null || !name.matches(NAME_RE)) return null;
        File f = new File(dir, name);
        return f.isFile() ? f : null;
    }

    synchronized boolean delete(String name) {
        File f = get(name);
        if (f == null || !f.delete()) return false;
        writeOrder(names());
        version++;
        return true;
    }

    synchronized void clear() {
        for (File f : list()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        orderFile.delete();
        version++;
    }

    /** 순서 바꾸기: dir = -1(앞으로) / +1(뒤로) */
    synchronized boolean move(String name, int step) {
        List<String> order = names();
        int i = order.indexOf(name);
        int j = i + (step < 0 ? -1 : 1);
        if (i < 0 || j < 0 || j >= order.size()) return false;
        order.set(i, order.get(j));
        order.set(j, name);
        writeOrder(order);
        version++;
        return true;
    }

    private List<String> names() {
        List<String> out = new ArrayList<>();
        for (File f : list()) out.add(f.getName());
        return out;
    }

    private List<String> readOrder() {
        List<String> out = new ArrayList<>();
        if (!orderFile.isFile()) return out;
        try (FileInputStream in = new FileInputStream(orderFile)) {
            byte[] b = new byte[(int) Math.min(orderFile.length(), 64 * 1024)];
            int n = in.read(b);
            if (n > 0) {
                for (String s : new String(b, 0, n, "UTF-8").split("\n")) {
                    s = s.trim();
                    if (s.matches(NAME_RE)) out.add(s);
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private void writeOrder(List<String> order) {
        StringBuilder b = new StringBuilder();
        for (String s : order) b.append(s).append('\n');
        try (FileOutputStream out = new FileOutputStream(orderFile)) {
            out.write(b.toString().getBytes("UTF-8"));
        } catch (IOException ignored) {
        }
    }

    private void cleanupTmp() {
        File[] tmps = dir.listFiles((d, name) -> name.endsWith(".tmp"));
        if (tmps != null) for (File f : tmps) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
