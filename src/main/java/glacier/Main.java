package glacier;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.glacier.AmazonGlacierClient;
import com.amazonaws.services.glacier.transfer.ArchiveTransferManager;
import com.amazonaws.services.glacier.transfer.UploadResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        Uploader uploader = new Uploader();
        FileChooser.createAndShowGUI(new Handler(uploader));
    }
}

class FileChooser extends JPanel implements ActionListener {
    private JButton openButton;
    private final JFileChooser fc;
    private final JTextField textField;
    private final FileChosenHandler handler;

    static FileChooser createAndShowGUI(FileChosenHandler handler) {
        JFrame frame = new JFrame("File Chooser");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        FileChooser chooser = new FileChooser(handler);
        frame.add(chooser);
        frame.pack();
        frame.setVisible(true);
        return chooser;
    }

    FileChooser(FileChosenHandler handler) {
        super(new BorderLayout());
        this.handler = handler;
        fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        openButton = new JButton("Open");
        openButton.addActionListener(this);
        textField = new JTextField(20);
        textField.addActionListener(this);
        // For layout purposes, put the buttons in a separate panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(openButton);
        buttonPanel.add(textField);
        add(buttonPanel, BorderLayout.PAGE_START);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == openButton) {
            fc.showOpenDialog(FileChooser.this);
            handler.handle(fc.getSelectedFile());
        } else if (e.getSource() instanceof JTextField) {
            handler.handleText(textField.getText());
        }
    }
}

class Uploader {
    private final AWSCredentials credentials;
    private final AmazonGlacierClient client;

    public Uploader() {
        try {
            credentials = new PropertiesCredentials(Thread.currentThread().getContextClassLoader().getResourceAsStream("AwsCredentials.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        client = new AmazonGlacierClient(credentials);
        client.setEndpoint("https://glacier.us-east-1.amazonaws.com/");
    }

    public void upload(File archive, String vaultName) {
        try {
            ArchiveTransferManager atm = new ArchiveTransferManager(client, credentials);
            final long bytesToTransfer = archive.length();
            UploadResult result = atm.upload("-", vaultName, archive.toString(), archive, new ProgressListener() {
                @Override
                public void progressChanged(ProgressEvent progressEvent) {
//                    double percent = 100.0 * progressEvent.getBytesTransferred() / bytesToTransfer;
//                    System.out.println(String.format("%.2f percent done", percent));
                }
            });
            System.out.println("Archive ID: " + result.getArchiveId());
        } catch (Exception e) {
            System.err.println(e);
        }
    }
}

interface FileChosenHandler {
    void handle(File file);
    void handleText(String text);
}

class Handler implements FileChosenHandler {
    private File chosenFile;
    private String vaultName;
    private final Uploader uploader;

    Handler(Uploader uploader) {
        this.uploader = uploader;
    }

    private void recurseDirectory(File root, List<File> files) {
        File[] children = root.listFiles();
        if (children == null || children.length < 1) {
            return;
        }
        for (File child : children) {
            if (child.isFile()) {
                files.add(child);
            } else if (child.isDirectory()) {
                recurseDirectory(child, files);
            }
        }
    }

    @Override
    public void handle(File file) {
        chosenFile = file;
        if (bothFieldsSet()) {
            go();
        }
    }

    private void go() {
        System.out.println("Vault name = " + vaultName);

        if (chosenFile.isDirectory()) {
            List<File> files = new ArrayList<>();
            recurseDirectory(chosenFile, files);
            for (File file : files) {
                uploader.upload(file.getAbsoluteFile(), vaultName);
            }
        } else {
            uploader.upload(chosenFile.getAbsoluteFile(), vaultName);
        }
    }

    @Override
    public void handleText(String text) {
        vaultName = text;
        if (bothFieldsSet()) {
            go();
        }
    }

    private boolean bothFieldsSet() {
        return chosenFile != null && vaultName != null && vaultName.trim().length() > 0;
    }
}
