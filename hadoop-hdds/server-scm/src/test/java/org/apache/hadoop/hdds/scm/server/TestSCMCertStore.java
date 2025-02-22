/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.scm.server;

import org.apache.hadoop.hdds.HddsConfigKeys;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.scm.metadata.SCMMetadataStore;
import org.apache.hadoop.hdds.scm.metadata.SCMMetadataStoreImpl;
import org.apache.hadoop.hdds.security.SecurityConfig;
import org.apache.hadoop.hdds.security.x509.CertificateTestUtils;
import org.apache.hadoop.hdds.security.x509.certificate.CertInfo;
import org.apache.hadoop.hdds.security.x509.certificate.authority.CRLApprover;
import org.apache.hadoop.hdds.security.x509.certificate.authority.CertificateStore;
import org.apache.hadoop.hdds.security.x509.certificate.authority.DefaultCRLApprover;
import org.apache.hadoop.hdds.security.x509.crl.CRLInfo;
import org.apache.hadoop.hdds.utils.db.Table;
import org.apache.hadoop.hdds.utils.db.TableIterator;
import org.apache.hadoop.security.ssl.KeyStoreTestUtil;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.X509CertificateHolder;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos.NodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.apache.hadoop.hdds.protocol.proto.HddsProtos.NodeType.DATANODE;
import static org.apache.hadoop.hdds.protocol.proto.HddsProtos.NodeType.OM;
import static org.apache.hadoop.hdds.protocol.proto.HddsProtos.NodeType.SCM;
import static org.apache.hadoop.hdds.security.x509.certificate.authority.CertificateStore.CertType.VALID_CERTS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.apache.hadoop.ozone.OzoneConsts.CRL_SEQUENCE_ID_KEY;

/**
 * Test class for @{@link SCMCertStore}.
 */
public class TestSCMCertStore {

  private static final String COMPONENT_NAME = "scm";
  private static final Long INITIAL_SEQUENCE_ID = 1L;

  private OzoneConfiguration config;
  private SCMMetadataStore scmMetadataStore;
  private CertificateStore scmCertStore;
  private SecurityConfig securityConfig;
  private X509Certificate x509Certificate;
  private KeyPair keyPair;
  private CRLApprover crlApprover;

  @BeforeEach
  public void setUp(@TempDir Path tempDir) throws Exception {
    config = new OzoneConfiguration();

    config.set(HddsConfigKeys.OZONE_METADATA_DIRS,
        tempDir.toAbsolutePath().toString());

    securityConfig = new SecurityConfig(config);
    keyPair = KeyStoreTestUtil.generateKeyPair("RSA");

    scmMetadataStore = new SCMMetadataStoreImpl(config);
    scmCertStore = new SCMCertStore.Builder().setRatisServer(null)
        .setCRLSequenceId(INITIAL_SEQUENCE_ID)
        .setMetadaStore(scmMetadataStore)
        .build();

    Files.createDirectories(securityConfig.getKeyLocation(COMPONENT_NAME));
    x509Certificate = generateX509Cert();

    crlApprover = new DefaultCRLApprover(securityConfig,
        keyPair.getPrivate());
  }

  @AfterEach
  public void destroyDbStore() throws Exception {
    if (scmMetadataStore.getStore() != null) {
      scmMetadataStore.getStore().close();
    }
  }

  @Test
  public void testRevokeCertificates() throws Exception {

    BigInteger serialID = x509Certificate.getSerialNumber();
    scmCertStore.storeValidCertificate(serialID, x509Certificate, SCM);
    Date now = new Date();

    assertNotNull(
        scmCertStore.getCertificateByID(serialID,
        VALID_CERTS));

    X509CertificateHolder caCertificateHolder =
        new X509CertificateHolder(generateX509Cert().getEncoded());
    List<BigInteger> certs = new ArrayList<>();
    certs.add(x509Certificate.getSerialNumber());
    Optional<Long> sequenceId = scmCertStore.revokeCertificates(certs,
        caCertificateHolder,
        CRLReason.lookup(CRLReason.keyCompromise), now, crlApprover);

    assertTrue(sequenceId.isPresent());
    assertEquals(INITIAL_SEQUENCE_ID + 1L, (long) sequenceId.get());

    assertNull(
        scmCertStore.getCertificateByID(serialID,
            VALID_CERTS));

    CertInfo certInfo = scmCertStore.getRevokedCertificateInfoByID(serialID);

    assertNotNull(certInfo);
    assertNotNull(certInfo.getX509Certificate());
    assertTrue(certInfo.getTimestamp() > 0L,
        "Timestamp should be greater than 0");

    long crlId = scmCertStore.getLatestCrlId();
    assertEquals(sequenceId.get().longValue(), crlId);

    List<CRLInfo> crls = scmCertStore.getCrls(Arrays.asList(crlId));
    assertEquals(1, crls.size());

    // CRL Info table should have a CRL with sequence id
    assertNotNull(scmMetadataStore.getCRLInfoTable()
        .get(sequenceId.get()));

    // Check the sequence ID table for latest sequence id
    assertEquals(INITIAL_SEQUENCE_ID + 1L, (long)
        scmMetadataStore.getCRLSequenceIdTable().get(CRL_SEQUENCE_ID_KEY));

    CRLInfo crlInfo = crls.get(0);

    assertEquals(crlInfo.getCrlSequenceID(), sequenceId.get().longValue());

    Set<? extends X509CRLEntry> revokedCertificates =
        crlInfo.getX509CRL().getRevokedCertificates();
    assertEquals(1L, revokedCertificates.size());
    assertEquals(x509Certificate.getSerialNumber(),
        revokedCertificates.iterator().next().getSerialNumber());

    // Now trying to revoke the already revoked certificate should result in
    // a warning message and no-op. It should not create a new CRL.
    sequenceId = scmCertStore.revokeCertificates(certs,
        caCertificateHolder,
        CRLReason.lookup(CRLReason.unspecified), now, crlApprover);

    assertFalse(sequenceId.isPresent());

    assertEquals(1L, getTableSize(scmMetadataStore.getCRLInfoTable()));

    // Generate 3 more certificates and revoke 2 of them
    List<BigInteger> newSerialIDs = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      X509Certificate cert = generateX509Cert();
      scmCertStore.storeValidCertificate(cert.getSerialNumber(), cert, SCM);
      newSerialIDs.add(cert.getSerialNumber());
    }

    // Add the first 2 certificates to the revocation list
    sequenceId = scmCertStore.revokeCertificates(newSerialIDs.subList(0, 2),
        caCertificateHolder,
        CRLReason.lookup(CRLReason.aACompromise), now, crlApprover);

    // This should create a CRL with sequence id INITIAL_SEQUENCE_ID + 2
    // And contain 2 certificates in it
    assertTrue(sequenceId.isPresent());
    assertEquals(sequenceId.get().longValue(),
        scmCertStore.getLatestCrlId());
    assertEquals(INITIAL_SEQUENCE_ID + 2L, (long) sequenceId.get());

    // Check the sequence ID table for latest sequence id
    assertEquals(INITIAL_SEQUENCE_ID + 2L, (long)
        scmMetadataStore.getCRLSequenceIdTable().get(CRL_SEQUENCE_ID_KEY));

    CRLInfo newCrlInfo = scmCertStore.getCrls(Arrays.asList(
        INITIAL_SEQUENCE_ID + 2)).get(0);
    revokedCertificates = newCrlInfo.getX509CRL().getRevokedCertificates();
    assertEquals(2L, revokedCertificates.size());
    assertNotNull(
        revokedCertificates.stream().filter(c ->
            c.getSerialNumber().equals(newSerialIDs.get(0)))
            .findAny());

    assertNotNull(
        revokedCertificates.stream().filter(c ->
            c.getSerialNumber().equals(newSerialIDs.get(1)))
            .findAny());

    assertEquals(newCrlInfo.getCrlSequenceID(), sequenceId.get().longValue());

    Table<BigInteger, X509Certificate> validCertsTable =
        scmMetadataStore.getValidCertsTable();
    try (TableIterator<BigInteger, ? extends Table.KeyValue<BigInteger,
        X509Certificate>> iterator = validCertsTable.iterator()) {
      assertTrue(iterator.hasNext());
      // Make sure that the last certificate that was not revoked is the one
      // in the valid certs table.
      assertEquals(newSerialIDs.get(2), iterator.next().getKey());
      // Valid certs table should have 1 cert
      assertFalse(iterator.hasNext());
    }

    // Revoked certs table should have 3 certs
    assertEquals(3L,
        getTableSize(scmMetadataStore.getRevokedCertsV2Table()));
  }

  @Test
  public void testRevokeCertificatesForFutureTime() throws Exception {
    BigInteger serialID = x509Certificate.getSerialNumber();
    scmCertStore.storeValidCertificate(serialID, x509Certificate, SCM);
    Date now = new Date();
    // Set revocation time in the future
    Date revocationTime = new Date(now.getTime() + 500);

    X509CertificateHolder caCertificateHolder =
        new X509CertificateHolder(generateX509Cert().getEncoded());
    List<BigInteger> certs = new ArrayList<>();
    certs.add(x509Certificate.getSerialNumber());
    Optional<Long> sequenceId = scmCertStore.revokeCertificates(certs,
        caCertificateHolder,
        CRLReason.lookup(CRLReason.keyCompromise), revocationTime,
        crlApprover);

    assertTrue(sequenceId.isPresent());
    assertEquals(INITIAL_SEQUENCE_ID + 1L, (long) sequenceId.get());

    assertNotNull(
        scmCertStore.getCertificateByID(serialID,
            VALID_CERTS));

    assertNull(
        scmCertStore.getRevokedCertificateInfoByID(serialID));
  }

  private X509Certificate generateX509Cert() throws Exception {
    return KeyStoreTestUtil.generateCertificate("CN=Test", keyPair, 30,
            "SHA256withRSA");
  }

  private long getTableSize(Table<?, ?> table) throws IOException {
    try (TableIterator<?, ?> iterator = table.iterator()) {
      long size = 0;

      while (iterator.hasNext()) {
        size++;
        iterator.next();
      }

      return size;
    }
  }

  @Test
  public void testGetAndListCertificates() throws Exception {
    X509Certificate cert = generateX509Cert();
    scmCertStore.storeValidCertificate(cert.getSerialNumber(), cert, SCM);
    checkListCerts(SCM, 1);

    cert = generateX509Cert();
    scmCertStore.storeValidCertificate(cert.getSerialNumber(), cert, SCM);
    checkListCerts(SCM, 2);

    cert = generateX509Cert();
    scmCertStore.storeValidCertificate(cert.getSerialNumber(), cert, SCM);
    checkListCerts(SCM, 3);

    cert = generateX509Cert();
    scmCertStore.storeValidCertificate(cert.getSerialNumber(), cert, OM);

    // As for OM and DN all certs in valid certs table are returned.
    // This test can be fixed once we have code for returning OM/DN certs.
    checkListCerts(OM, 4);

    cert = generateX509Cert();
    scmCertStore.storeValidCertificate(cert.getSerialNumber(), cert, DATANODE);
    checkListCerts(OM, 5);

  }

  @Test
  public void testRemoveAllCertificates() throws Exception {
    X509Certificate scmCert = CertificateTestUtils.createSelfSignedCert(
        keyPair, "1", Duration.ofDays(1), BigInteger.valueOf(1));
    X509Certificate expiredScmCert = CertificateTestUtils.createSelfSignedCert(
        keyPair, "2", Duration.ofNanos(1), BigInteger.valueOf(2));
    X509Certificate nonScmCert = CertificateTestUtils.createSelfSignedCert(
        keyPair, "3", Duration.ofDays(1), BigInteger.valueOf(3));
    X509Certificate expiredNonScmCert =
        CertificateTestUtils.createSelfSignedCert(
            keyPair, "4", Duration.ofNanos(1), BigInteger.valueOf(4));
    scmCertStore.storeValidCertificate(
        scmCert.getSerialNumber(), scmCert, SCM);
    scmCertStore.storeValidCertificate(
        expiredScmCert.getSerialNumber(), expiredScmCert, SCM);
    scmCertStore.storeValidCertificate(
        nonScmCert.getSerialNumber(), nonScmCert, OM);
    scmCertStore.storeValidCertificate(
        expiredNonScmCert.getSerialNumber(), expiredNonScmCert, OM);
    //Listing OM certs still lists SCM certificates as well
    checkListCerts(OM, 4);
    checkListCerts(SCM, 2);
    scmCertStore.removeAllExpiredCertificates();
    checkListCerts(OM, 2);
    checkListCerts(SCM, 1);
  }

  private void checkListCerts(NodeType role, int expected) throws Exception {
    List<X509Certificate> certificateList = scmCertStore.listCertificate(role,
        BigInteger.valueOf(0), 10, VALID_CERTS);
    Assertions.assertEquals(expected, certificateList.size());
  }
}
