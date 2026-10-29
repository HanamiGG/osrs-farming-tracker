package com.headroomai.farmwatch;

import com.headroomai.farmwatch.model.AccountState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes JSON snapshots to disk. Atomic replace via temp file + ATOMIC_MOVE.
 * File writes happen on a single-thread background executor so they can't
 * block the client tick thread.
 */
@Slf4j
public class StateExporter
{
    private final Path exportDir;
    private final FarmwatchConfig config;
    private final ExecutorService io;
    private final AtomicLong writesInFlight = new AtomicLong();

    public StateExporter(Path exportDir, FarmwatchConfig config) throws IOException
    {
        this.exportDir = exportDir;
        this.config = config;
        Files.createDirectories(exportDir);

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "farmwatch-exporter");
            t.setDaemon(true);
            return t;
        };
        this.io = Executors.newSingleThreadExecutor(tf);
    }

    public void flushAsync(FarmwatchState state)
    {
        if (writesInFlight.get() > 2)
        {
            // backpressure: drop writes if we're already behind
            return;
        }
        writesInFlight.incrementAndGet();
        io.submit(() -> {
            try
            {
                flush(state);
            }
            catch (Exception e)
            {
                log.warn("Farmwatch snapshot write failed", e);
            }
            finally
            {
                writesInFlight.decrementAndGet();
            }
        });
    }

    public synchronized void flush(FarmwatchState state) throws IOException
    {
        byte[] all = JsonCodec.encodeAll(state);
        writeAtomic(exportDir.resolve("state.json"), all);

        for (AccountState acc : state.all())
        {
            byte[] bytes = JsonCodec.encodeOne(state, acc);
            writeAtomic(exportDir.resolve("state-" + Long.toUnsignedString(acc.getHash()) + ".json"), bytes);
        }
    }

    private void writeAtomic(Path target, byte[] bytes) throws IOException
    {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try
        {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException atomicFail)
        {
            // Windows sometimes rejects ATOMIC_MOVE across filesystems; fall back.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
