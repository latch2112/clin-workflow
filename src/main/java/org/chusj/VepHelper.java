package org.chusj;


import com.sun.tools.internal.jxc.ap.Const;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.NumberFormat;
import java.text.ParseException;

import static org.chusj.VEPSparkDriverProgram.getSHA256Hash;

public class VepHelper {

    private static float avgFuncAnnoPerMutation = 0.0f;
    private static int countFuncAnnoPerMutation = 0;
    private static int countMutation =0;
    private static NumberFormat NF = NumberFormat.getInstance();
    private static JSONObject lastOne;
    private static final int CLINVAR = 0;
    private static final int OMIM = 1;
    private static final int ENSEMBL = 2;
    private static final int ORPHANET = 3;
    private static final int DBSNP = 4;
    private static final int PUBMED = 5;


    public static void main(String[] args) throws Exception {

        String extractFile = args[0];
        String nbPatient = args[1];

        try (BufferedReader buf = new BufferedReader(new FileReader(extractFile))) {

            String fetchedLine;


            // Record and prep metaData
            buf.readLine();

            // Main - Child=14140,Mother=14141,Father=14142

            String[] pedigree = {"14140","14141", "14142"};

            while (true) {
                fetchedLine = buf.readLine();
                if (fetchedLine == null) {
                    break;
                } else {
                    JSONObject propertiesOneMutation = processVcfDataLine(fetchedLine, "dn,dq", pedigree);
                    propertiesOneMutation.put("assemblyVersion", "GRCh38");
                    propertiesOneMutation.put("annotationTool", "VEP");
                    propertiesOneMutation.put("annotationToolVersion", 96.3);
                    lastOne = propertiesOneMutation;
                    // extract donor info if not found
//                    String qual = (String) propertiesOneMutation.remove("qual");
//                    String filter = (String) propertiesOneMutation.remove("filter");
                    JSONArray donorArray = (JSONArray) propertiesOneMutation.remove("donor");
                    System.out.println("donors:" + donorArray.toString(2));
//                    for (int i=0; i<donorArray.length(); i++) {
//                        JSONObject donor = (JSONObject) donorArray.get(i);
//                        System.out.println("donors:" + donor.toString(2));
//                    }


                }
            }
        }
        if (countMutation>0) {
            avgFuncAnnoPerMutation = (float) countFuncAnnoPerMutation / countMutation;
        }
        System.out.println("\navgFuncAnnoPerMutation="+avgFuncAnnoPerMutation);
        System.out.println("lastOne="+lastOne.toString(2));

    }


    public static JSONObject processVcfDataLine(String extractedLine, String OptionStr, String[] pedigree) {

        //CHROM	POS	ID	REF	ALT	QUAL	FILTER	DP	MQ	MQRankSum
        // ReadPosRankSum	LOD	FractionInformativeReads	SNP	MNP	INS	DEL	MIXED	HOM	HET
        // GEN[*].GT	GEN[*].AD	GEN[*].AF	GEN[*].F1R2	GEN[*].F2R1	GEN[*].DP	GEN[*].SB	GEN[*].MB	CSQ
        //System.out.print(".");
        String[] lineValueArray = extractedLine.split("\t");
        // System.out.println("lineValueArray.length="+lineValueArray.length);
        // dynamic positioning system -- pos counter++
        int pos = 0;

        //boolean db_snp = flase, clinvar = false, omim = false, emsembl = false, orphanet = false , pubmed=false;
        boolean[] dbExt = {false, false, false, false, false, false};

        JSONObject propertiesOneMutation = new JSONObject();
        JSONArray donorArray = new JSONArray();
        JSONArray phenotypesArray = new JSONArray();
        JSONArray bdExtArray = new JSONArray();

        int nbDonor = pedigree.length;
        JSONObject[] arrayDonor = new JSONObject[nbDonor];
        for (int i=0; i<nbDonor; i++) {
            arrayDonor[i] = new JSONObject();
        }


        String chrom = lineValueArray[pos++];
        if ("CHROM".equalsIgnoreCase(chrom)) {
            return null; // Meta data line
        }
        countMutation++;
        String position = lineValueArray[pos++];

        if (addStrToJsonObject("dbSNP_ID", lineValueArray[pos++], propertiesOneMutation, false)) {
            // bdExtArray.put( new JSONObject().put("dbSNP",
            dbExt[DBSNP] =  true;
        }
        String reference = lineValueArray[pos++];
        String alt = lineValueArray[pos++].replace(",<NON_REF>", ""); // CT,<NON_REF> or G,TGG,<NON_REF>
        String qual = lineValueArray[pos++];
        String filter = lineValueArray[pos++];

        String dpS = lineValueArray[pos++];
        String mqS = lineValueArray[pos++];
        String mqRankSum = lineValueArray[pos++];
        String readPosRankSum = lineValueArray[pos++];
        String lod = lineValueArray[pos++];
        String fractionInformativeReads = lineValueArray[pos++];

        boolean snp = Boolean.valueOf(lineValueArray[pos++]);
        boolean mnp = Boolean.valueOf(lineValueArray[pos++]);
        boolean ins = Boolean.valueOf(lineValueArray[pos++]);
        boolean del = Boolean.valueOf(lineValueArray[pos++]);
        boolean mixed = Boolean.valueOf(lineValueArray[pos++]);
        boolean hom = Boolean.valueOf(lineValueArray[pos++]);
        boolean het = Boolean.valueOf(lineValueArray[pos++]);


        if (snp) propertiesOneMutation.put("type", "SNP");
        if (mnp) propertiesOneMutation.put("type", "MNP");
        if (ins) propertiesOneMutation.put("type", "INS");
        if (del) propertiesOneMutation.put("type", "DEL");
        if (mixed) propertiesOneMutation.put("type", "MIXED");

        propertiesOneMutation.put("alt", alt);

        String[] gt = lineValueArray[pos++].split(",");
        String[] gq = lineValueArray[pos++].split(",");
        String adStr = lineValueArray[pos++];
        //System.out.println(adStr);
        String[] ad = adStr.split(",", -1);
        String[] af = lineValueArray[pos++].split(",");
        String[] f1r2 = lineValueArray[pos++].split(",");
        String[] f2r1 = lineValueArray[pos++].split(",");
        String[] genDP = lineValueArray[pos++].split(",");
        String[] sb = lineValueArray[pos++].split(",");
        String[] mb = lineValueArray[pos++].split(",");


        String dnS = lineValueArray[pos++];
        String dqS = lineValueArray[pos++];
        String hiConfDeNovo = lineValueArray[pos++];

        String csq = lineValueArray[pos++];
        String[] csqArray = csq.split(",");  // return functionalAnnotation array
        countFuncAnnoPerMutation += csqArray.length;

        String chrPos = chrom.substring(3); // remove 'chr'
        String mutation = reference + ">" + alt.split(",")[0];
        String dnaChanges = chrPos + ":g." + position + mutation;
        String uid = getSHA256Hash(dnaChanges);

        propertiesOneMutation.put("id", uid);
        propertiesOneMutation.put("mutationId", dnaChanges);
        propertiesOneMutation.put("mutation", mutation);
        propertiesOneMutation.put("chrom",chrPos);



//        JSONObject funcAnnotation = null;

        JSONArray functionalAnnotations = new JSONArray();

        for (String s : csqArray) {
            functionalAnnotations.put(processVepAnnotations(s, bdExtArray, dbExt));
        }

        propertiesOneMutation.put("functionalAnnotations", functionalAnnotations);

        for (int i=0; i< nbDonor; i++) {

            arrayDonor[i].put("phenotypes", phenotypesArray);
            addNumberToJsonObject("quality", qual, arrayDonor[i], false);
            arrayDonor[i].put("filter", filter);
            arrayDonor[i].put("gt", gt[i]);
            addNumberToJsonObject("gq", gq[i], arrayDonor[i], false);
            String adS = null;
            // bug in snpSift Extract...  does not keep correctly unknown value . in GEN[*].AD
            // but in that regards, the information is so bad that it's okay to put 0,1 or 1,0
            if (ad.length < nbDonor * 2) {
                if ("./.".equalsIgnoreCase(gt[i])) {
                    adS = "0,0";
                } else if ("1/0".equalsIgnoreCase(gt[i])) {
                    adS = "0,1";
                } else if ("0/1".equalsIgnoreCase(gt[i])) {
                    adS = "0,1";
                } else { //1/1
                    adS = "1,0";
                }
            } else {
                adS = ad[i * 2] + "," + ad[(i * 2) + 1];
            }
            arrayDonor[i].put("ad", adS);
            arrayDonor[i].put("af", af[i]);
            arrayDonor[i].put("f1r2", f1r2[i*2] + "," + f1r2[i*2+1]);
            arrayDonor[i].put("f2r1", f2r1[i*2] + "," + f2r1[i*2+1]);
            arrayDonor[i].put("dp", genDP[i]);
            arrayDonor[i].put("sb", sb[i]);
            arrayDonor[i].put("mb", mb[i]);
            addNumberToJsonObject("mq", mqS, arrayDonor[i], false);
            addNumberToJsonObject("mqRankSum", mqRankSum, arrayDonor[i], false);
            addNumberToJsonObject("depth", dpS, arrayDonor[i], false);
            addNumberToJsonObject("readPosRankSum", readPosRankSum, arrayDonor[i], false);
            arrayDonor[i].put("donorId", pedigree[i]);


            donorArray.put(arrayDonor[i]);

        }

        bdExtArray.put( new JSONObject().put("dbSNP", dbExt[DBSNP] ));
        bdExtArray.put( new JSONObject().put("clinvar", dbExt[CLINVAR] ));
        bdExtArray.put( new JSONObject().put("ensembl", dbExt[ENSEMBL] ));
        bdExtArray.put( new JSONObject().put("omim", dbExt[OMIM] ));
        bdExtArray.put( new JSONObject().put("orphanet", dbExt[ORPHANET] ));

        propertiesOneMutation.put("donor", donorArray);
        propertiesOneMutation.put("bdExt", bdExtArray);

        return propertiesOneMutation;

    }

    private static JSONObject processVepAnnotations(String csqLine, JSONArray bdExtArray, boolean[] dbExt ) {

        //System.out.print("\n"+csqLine);
        String[] functionalAnnotationArray = csqLine.split("[|]", -1);
        // dynamic positioning system -- pos counter++

//        for (String s : functionalAnnotationArray) {
//            String val = s;
//
//            //System.out.print(i+"'"+val+"'");
//        }

        //System.out.print(id+"("+functionalAnnotationArray.length+")");
        //CHROM	POS	ID	REF	ALT	QUAL	FILTER	DP	MQ	MQRankSum	ReadPosRankSum	LOD	FractionInformativeReads	SNP	MNP	INS	DEL	MIXED	HOM	HET	GEN[*].GT	GEN[*].GQ	GEN[*].AD	GEN[*].AF	GEN[*].F1R2	GEN[*].F2R1	GEN[*].DP	GEN[*].SB	GEN[*].MB	GEN[*].DN	GEN[*].DQ	hiConfDeNovo	CSQ
        //CHROM	POS	ID	REF	ALT	QUAL	FILTER	DP	MQ	MQRankSum	ReadPosRankSum	LOD	FractionInformativeReads	SNP	MNP	INS	DEL	MIXED	HOM	HET	GEN[*].GT	            GEN[*].AD	GEN[*].AF	GEN[*].F1R2	GEN[*].F2R1	GEN[*].DP	GEN[*].SB	GEN[*].MB	CSQ

        int pos = 0;

        if (functionalAnnotationArray.length < 403) {
            System.out.println(" " + csqLine + " short");
        }

        JSONObject funcAnnotation = new JSONObject();
        JSONObject funcAnnoProperties = new JSONObject();
        JSONObject frequencies = new JSONObject();
        JSONObject frequencyExAc = new JSONObject();
        JSONObject frequency1000Gp3 = new JSONObject();
        JSONObject frequencyEsp6500 = new JSONObject();
        JSONObject frequencyGnomadEx = new JSONObject();
        JSONObject frequencyGnomadGen = new JSONObject();
        JSONObject prediction = new JSONObject();



        //0 -
        addStrToJsonObject("allele", functionalAnnotationArray[pos++], funcAnnotation, false);
//        String Consequence = functionalAnnotationArray[pos++];
        addStrToJsonObject("consequence", functionalAnnotationArray[pos++], funcAnnotation, false);
        addStrToJsonObject("impact", functionalAnnotationArray[pos++], funcAnnotation, false);
        addStrToJsonObject("gene", functionalAnnotationArray[pos++], funcAnnotation, false);
        addStrToJsonObject("geneId", functionalAnnotationArray[pos++], funcAnnotation, false);
        addStrToJsonObject("featureType", functionalAnnotationArray[pos++], funcAnnotation, false);
        addStrToJsonObject("featureId", functionalAnnotationArray[pos++], funcAnnotation, false);
        addStrToJsonObject("biotype", functionalAnnotationArray[pos++], funcAnnotation, false);
        //String EXON = functionalAnnotationArray[pos++];
        addStrToJsonObject("exon", functionalAnnotationArray[pos++], funcAnnotation, false);
        //String INTRON = functionalAnnotationArray[pos++];
        addStrToJsonObject("intron", functionalAnnotationArray[pos++], funcAnnotation, false);
        // 9 -
        addStrToJsonObject("hgvsC", functionalAnnotationArray[pos++], funcAnnotation, false);
        addStrToJsonObject("hgvsP", functionalAnnotationArray[pos++], funcAnnotation, false);
        addNumberToJsonObject("cdnaPos", functionalAnnotationArray[pos++] , funcAnnotation, false);
        addNumberToJsonObject("cdsPos", functionalAnnotationArray[pos++] , funcAnnotation, false);
        //String Protein_position = functionalAnnotationArray[pos++];
        addNumberToJsonObject("ProteinPos", functionalAnnotationArray[pos++] , funcAnnotation, false);
        String Amino_acids = functionalAnnotationArray[pos++];
        //addNumberToJsonObject("aaLen", functionalAnnotationArray[pos++] , funcAnnotation, false);
//        String Codons = functionalAnnotationArray[pos++];
        addStrToJsonObject("codons", functionalAnnotationArray[pos++], funcAnnotation, false);
        String Existing_variation = functionalAnnotationArray[pos++];
        addNumberToJsonObject("distance", functionalAnnotationArray[pos++] , funcAnnotation, false);
        addNumberToJsonObject("strand", functionalAnnotationArray[pos++] , funcAnnotation, false);
        //19 -
        String FLAGS = functionalAnnotationArray[pos++];
        String SYMBOL_SOURCE = functionalAnnotationArray[pos++];
        String HGNC_ID = functionalAnnotationArray[pos++];
        String CANONICAL = functionalAnnotationArray[pos++];
        String GIVEN_REF = functionalAnnotationArray[pos++];
        String USED_REF = functionalAnnotationArray[pos++];
        String BAM_EDIT = functionalAnnotationArray[pos++];

        addNumberToJsonObject("AC", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("AF", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("AFR_AC", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        // 29
        addNumberToJsonObject("AFR_AF", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("AMR_AC", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("AMR_AF", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("EAS_AC", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("EAS_AF", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("EUR_AC", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("EUR_AF", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("SAS_AC", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        addNumberToJsonObject("SAS_AF", functionalAnnotationArray[pos++] , frequency1000Gp3, true);
        String ALSPAC_AC = functionalAnnotationArray[pos++];
        //39 -
        String ALSPAC_AF = functionalAnnotationArray[pos++];
        String APPRIS = functionalAnnotationArray[pos++];
        String Aloft_Confidence = functionalAnnotationArray[pos++];
        String Aloft_Fraction_transcripts_affected = functionalAnnotationArray[pos++];
        String Aloft_pred = functionalAnnotationArray[pos++];
        String Aloft_prob_Dominant = functionalAnnotationArray[pos++];
        String Aloft_prob_Recessive = functionalAnnotationArray[pos++];
        String Aloft_prob_Tolerant = functionalAnnotationArray[pos++];
        String AltaiNeandertal = functionalAnnotationArray[pos++];
        String Ancestral_allele = functionalAnnotationArray[pos++];
        //49 -
        String CADD_phred = functionalAnnotationArray[pos++];
        String CADD_raw = functionalAnnotationArray[pos++];
        String CADD_raw_rankscore = functionalAnnotationArray[pos++];
        String DANN_rankscore = functionalAnnotationArray[pos++];
        String DANN_score = functionalAnnotationArray[pos++];
        String DEOGEN2_pred = functionalAnnotationArray[pos++];
        String DEOGEN2_rankscore = functionalAnnotationArray[pos++];
        String DEOGEN2_score = functionalAnnotationArray[pos++];
        String Denisova = functionalAnnotationArray[pos++];
        addNumberToJsonObject("AA_AC", functionalAnnotationArray[pos++] , frequencyEsp6500, true);
        //59 -
        addNumberToJsonObject("AA_AF", functionalAnnotationArray[pos++] , frequencyEsp6500, true);
        addNumberToJsonObject("EA_AC", functionalAnnotationArray[pos++] , frequencyEsp6500, true);
        addNumberToJsonObject("EA_AF", functionalAnnotationArray[pos++] , frequencyEsp6500, true);
        String Eigen_PC_phred_coding = functionalAnnotationArray[pos++];
        String Eigen_PC_raw_coding = functionalAnnotationArray[pos++];
        String Eigen_PC_raw_coding_rankscore = functionalAnnotationArray[pos++];
        String Eigen_pred_coding = functionalAnnotationArray[pos++];
        String Eigen_raw_coding = functionalAnnotationArray[pos++];
        String Eigen_raw_coding_rankscore = functionalAnnotationArray[pos++];
        if (addStrToJsonObject("ensembl_geneid", functionalAnnotationArray[pos++], funcAnnotation, false)) {
            dbExt[ENSEMBL] = true;
        }
        // 69 -
        String Ensembl_proteinid = functionalAnnotationArray[pos++];
        String Ensembl_transcriptid = functionalAnnotationArray[pos++];
        addNumberToJsonObject("AC", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("AF", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("AFR_AC", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("AFR_AF", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("AMR_AC", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("AMR_AF", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("Adj_AC", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("Adj_AF", functionalAnnotationArray[pos++] , frequencyExAc, true);
        // 79
        addNumberToJsonObject("EAS_AC", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("EAS_AF", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("FIN_AC", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("FIN_AF", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("NFE_AC", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("NFE_AF", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("SAS_AC", functionalAnnotationArray[pos++] , frequencyExAc, true);
        addNumberToJsonObject("SAS_AF", functionalAnnotationArray[pos++] , frequencyExAc, true);
        String ExAC_nonTCGA_AC = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_AF = functionalAnnotationArray[pos++];
        // 89
        String ExAC_nonTCGA_AFR_AC = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_AFR_AF = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_AMR_AC = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_AMR_AF = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_Adj_AC = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_Adj_AF = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_EAS_AC = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_EAS_AF = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_FIN_AC = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_FIN_AF = functionalAnnotationArray[pos++];
        // 99
        String ExAC_nonTCGA_NFE_AC = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_NFE_AF = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_SAS_AC = functionalAnnotationArray[pos++];
        String ExAC_nonTCGA_SAS_AF = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_AC = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_AF = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_AFR_AC = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_AFR_AF = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_AMR_AC = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_AMR_AF = functionalAnnotationArray[pos++];
        // 109
        String ExAC_nonpsych_Adj_AC = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_Adj_AF = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_EAS_AC = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_EAS_AF = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_FIN_AC = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_FIN_AF = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_NFE_AC = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_NFE_AF = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_SAS_AC = functionalAnnotationArray[pos++];
        String ExAC_nonpsych_SAS_AF = functionalAnnotationArray[pos++];
        // 119
        String FATHMM_converted_rankscore = functionalAnnotationArray[pos++];
        addStrToJsonObject("FATHMM", functionalAnnotationArray[pos++], prediction, true);
        addStrToJsonObject("FATHMM_score", functionalAnnotationArray[pos++], prediction, true);
        String GENCODE_basic = functionalAnnotationArray[pos++];
        String GERPpp_NR = functionalAnnotationArray[pos++];
        String GERPpp_RS = functionalAnnotationArray[pos++];
        String GERPpp_RS_rankscore = functionalAnnotationArray[pos++];
        String GM12878_confidence_value = functionalAnnotationArray[pos++];
        String GM12878_fitCons_rankscore = functionalAnnotationArray[pos++];
        String GM12878_fitCons_score = functionalAnnotationArray[pos++];
        // 129
        String GTEx_V7_gene = functionalAnnotationArray[pos++];
        String GTEx_V7_tissue = functionalAnnotationArray[pos++];
        String GenoCanyon_rankscore = functionalAnnotationArray[pos++];
        String GenoCanyon_score = functionalAnnotationArray[pos++];
        String Geuvadis_eQTL_target_gene = functionalAnnotationArray[pos++];
        String H1_hESC_confidence_value = functionalAnnotationArray[pos++];
        String H1_hESC_fitCons_rankscore = functionalAnnotationArray[pos++];
        String H1_hESC_fitCons_score = functionalAnnotationArray[pos++];
        String HGVSc_ANNOVAR = functionalAnnotationArray[pos++];
        String HGVSc_VEP = functionalAnnotationArray[pos++];
        // 139
        String HGVSc_snpEff = functionalAnnotationArray[pos++];
        String HGVSp_ANNOVAR = functionalAnnotationArray[pos++];
        String HGVSp_VEP = functionalAnnotationArray[pos++];
        String HGVSp_snpEff = functionalAnnotationArray[pos++];
        String HUVEC_confidence_value = functionalAnnotationArray[pos++];
        String HUVEC_fitCons_rankscore = functionalAnnotationArray[pos++];
        String HUVEC_fitCons_score = functionalAnnotationArray[pos++];
        String Interpro_domain = functionalAnnotationArray[pos++];
        String LINSIGHT = functionalAnnotationArray[pos++];
        String LINSIGHT_rankscore = functionalAnnotationArray[pos++];
        // 149
        String LRT_Omega = functionalAnnotationArray[pos++];
        String LRT_converted_rankscore = functionalAnnotationArray[pos++];
        String LRT_pred = functionalAnnotationArray[pos++];
        String LRT_score = functionalAnnotationArray[pos++];
        String M_CAP_pred = functionalAnnotationArray[pos++];
        String M_CAP_rankscore = functionalAnnotationArray[pos++];
        String M_CAP_score = functionalAnnotationArray[pos++];
        String MPC_rankscore = functionalAnnotationArray[pos++];
        String MPC_score = functionalAnnotationArray[pos++];
        String MVP_rankscore = functionalAnnotationArray[pos++];
        // 159
        String MVP_score = functionalAnnotationArray[pos++];
        String MetaLR_pred = functionalAnnotationArray[pos++];
        String MetaLR_rankscore = functionalAnnotationArray[pos++];
        String MetaLR_score = functionalAnnotationArray[pos++].trim();
        String MetaSVM_pred = functionalAnnotationArray[pos++].trim();
        String MetaSVM_rankscore = functionalAnnotationArray[pos++].trim();
        String MetaSVM_score = functionalAnnotationArray[pos++].trim();
        String MutPred_AAchange = functionalAnnotationArray[pos++].trim();
        String MutPred_Top5features = functionalAnnotationArray[pos++].trim();
        String MutPred_protID = functionalAnnotationArray[pos++].trim();
        // 169
        String MutPred_rankscore = functionalAnnotationArray[pos++].trim();
        String MutPred_score = functionalAnnotationArray[pos++].trim();
        String MutationAssessor_pred = functionalAnnotationArray[pos++].trim();
        String MutationAssessor_rankscore = functionalAnnotationArray[pos++].trim();
        String MutationAssessor_score = functionalAnnotationArray[pos++].trim();
        String MutationTaster_AAE = functionalAnnotationArray[pos++].trim();
        String MutationTaster_converted_rankscore = functionalAnnotationArray[pos++].trim();
        String MutationTaster_model = functionalAnnotationArray[pos++].trim();
        String MutationTaster_pred = functionalAnnotationArray[pos++].trim();
        String MutationTaster_score = functionalAnnotationArray[pos++].trim();
        // 179
        String PROVEAN_converted_rankscore = functionalAnnotationArray[pos++].trim();
        String PROVEAN_pred = functionalAnnotationArray[pos++].trim();
        String PROVEAN_score = functionalAnnotationArray[pos++].trim();
        addStrToJsonObject("Polyphen2_HDIV", functionalAnnotationArray[pos++].trim(), prediction, true);
        String Polyphen2_HDIV_rankscore = functionalAnnotationArray[pos++].trim();
        addStrToJsonObject("Polyphen2_HDIV_score", functionalAnnotationArray[pos++], prediction, true);
        addStrToJsonObject("Polyphen2_HVAR_pred", functionalAnnotationArray[pos++].trim(), prediction, true);
        String Polyphen2_HVAR_rankscore = functionalAnnotationArray[pos++].trim();
        addStrToJsonObject("Polyphen2_HVAR_score", functionalAnnotationArray[pos++], prediction, true);
        String PrimateAI_pred = functionalAnnotationArray[pos++].trim();
        // 189
        String PrimateAI_rankscore = functionalAnnotationArray[pos++].trim();
        String PrimateAI_score = functionalAnnotationArray[pos++].trim();
        String REVEL_rankscore = functionalAnnotationArray[pos++].trim();
        String REVEL_score = functionalAnnotationArray[pos++].trim();
        String Reliability_index = functionalAnnotationArray[pos++].trim();
        String SIFT4G_converted_rankscore = functionalAnnotationArray[pos++].trim();
        String SIFT4G_pred = functionalAnnotationArray[pos++].trim();
        String SIFT4G_score = functionalAnnotationArray[pos++].trim();
        String SIFT_converted_rankscore = functionalAnnotationArray[pos++].trim();
        addStrToJsonObject("SIFT", functionalAnnotationArray[pos++], prediction, true);
        // 199
        addStrToJsonObject("SIFT_score", functionalAnnotationArray[pos++].trim(), prediction, true);
        String SiPhy_29way_logOdds = functionalAnnotationArray[pos++].trim();
        String SiPhy_29way_logOdds_rankscore = functionalAnnotationArray[pos++].trim();
        String SiPhy_29way_pi = functionalAnnotationArray[pos++].trim();
        String TSL = functionalAnnotationArray[pos++];
        String TWINSUK_AC = functionalAnnotationArray[pos++];
        String TWINSUK_AF = functionalAnnotationArray[pos++];
        String UK10K_AC = functionalAnnotationArray[pos++];
        String UK10K_AF = functionalAnnotationArray[pos++];
        String Uniprot_acc = functionalAnnotationArray[pos++];
        // 209
        String Uniprot_entry = functionalAnnotationArray[pos++];
        String VEP_canonical = functionalAnnotationArray[pos++];
        String VEST4_rankscore = functionalAnnotationArray[pos++];
        String VEST4_score = functionalAnnotationArray[pos++];
        String VindijiaNeandertal = functionalAnnotationArray[pos++];
//        String aaalt = functionalAnnotationArray[pos++];
        addStrToJsonObject("aaAlt", functionalAnnotationArray[pos++], funcAnnotation, false);
        //String aapos = functionalAnnotationArray[pos++];
        addNumberToJsonObject("aaPos", functionalAnnotationArray[pos++] , funcAnnotation, false);
//        String aaref = functionalAnnotationArray[pos++];
        addStrToJsonObject("aaRef", functionalAnnotationArray[pos++], funcAnnotation, false);
//        String alt = functionalAnnotationArray[pos++];
        addStrToJsonObject("alt", functionalAnnotationArray[pos++], funcAnnotation, false);
        String bStatistic = functionalAnnotationArray[pos++];
        // 219
        String bStatistic_rankscore = functionalAnnotationArray[pos++];
        String cds_strand = functionalAnnotationArray[pos++];
        String chr = functionalAnnotationArray[pos++];
        String clinvar_MedGen_id = functionalAnnotationArray[pos++];
        if (addStrToJsonObject("clinvar_OMIM_id", functionalAnnotationArray[pos++], funcAnnotation, false)){
            dbExt[OMIM] = true;
        }
        if (addStrToJsonObject("clinvar_Orphanet_id", functionalAnnotationArray[pos++], funcAnnotation, false)){
            dbExt[ORPHANET] = true;
        }
        addStrToJsonObject("clinvar_clnsig", functionalAnnotationArray[pos++], funcAnnotation, false);
        addStrToJsonObject("clinvar_hgvs", functionalAnnotationArray[pos++], funcAnnotation, false);
        if ( addStrToJsonObject("clinvar_id", functionalAnnotationArray[pos++], funcAnnotation, false)){
            dbExt[CLINVAR] = true;
        }
        String clinvar_review = functionalAnnotationArray[pos++];
        // 229
        addStrToJsonObject("clinvar_trait", functionalAnnotationArray[pos++], funcAnnotation, false);
        String clinvar_var_source = functionalAnnotationArray[pos++];
        String codon_degeneracy = functionalAnnotationArray[pos++];
        String codonpos = functionalAnnotationArray[pos++];
        String fathmm_MKL_coding_group = functionalAnnotationArray[pos++];
        String fathmm_MKL_coding_pred = functionalAnnotationArray[pos++];
        String fathmm_MKL_coding_rankscore = functionalAnnotationArray[pos++];
        String fathmm_MKL_coding_score = functionalAnnotationArray[pos++];
        String fathmm_XF_coding_pred = functionalAnnotationArray[pos++];
        String fathmm_XF_coding_rankscore = functionalAnnotationArray[pos++];
        // 239
        String fathmm_XF_coding_score = functionalAnnotationArray[pos++];
//        String genename = functionalAnnotationArray[pos++];
        addStrToJsonObject("geneName", functionalAnnotationArray[pos++], funcAnnotation, false);

        addNumberToJsonObject("AC", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("AFR_AC", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("AFR_AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);

        String gnomAD_exomes_AFR_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_AFR_nhomalt = functionalAnnotationArray[pos++];

        addNumberToJsonObject("AMR_AC", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("AMR_AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        // 249
        String gnomAD_exomes_AMR_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_AMR_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_AN = functionalAnnotationArray[pos++];

        addNumberToJsonObject("ASJ_AC", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("ASJ_AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        String gnomAD_exomes_ASJ_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_ASJ_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_EAS_AC = functionalAnnotationArray[pos++];

        addNumberToJsonObject("EAS_AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("EAS_AN", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        // 259
        String gnomAD_exomes_EAS_nhomalt = functionalAnnotationArray[pos++];

        addNumberToJsonObject("FIN_AC", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("FIN_AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        String gnomAD_exomes_FIN_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_FIN_nhomalt = functionalAnnotationArray[pos++];
        addNumberToJsonObject("NFE_AC", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("NFE_AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        String gnomAD_exomes_NFE_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_NFE_nhomalt = functionalAnnotationArray[pos++];
        // 269
        addNumberToJsonObject("POPMAX_AC", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        // 269
        addNumberToJsonObject("POPMAX_AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        String gnomAD_exomes_POPMAX_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_POPMAX_nhomalt = functionalAnnotationArray[pos++];
        addNumberToJsonObject("SAS_AC", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        addNumberToJsonObject("SAS_AF", functionalAnnotationArray[pos++] , frequencyGnomadEx, true);
        String gnomAD_exomes_SAS_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_SAS_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AC = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AF = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AFR_AC = functionalAnnotationArray[pos++];
        // 279
        String gnomAD_exomes_controls_AFR_AF = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AFR_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AFR_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AMR_AC = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AMR_AF = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AMR_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AMR_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_ASJ_AC = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_ASJ_AF = functionalAnnotationArray[pos++];
        // 289
        String gnomAD_exomes_controls_ASJ_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_ASJ_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_EAS_AC = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_EAS_AF = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_EAS_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_EAS_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_FIN_AC = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_FIN_AF = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_FIN_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_FIN_nhomalt = functionalAnnotationArray[pos++];
        // 299
        String gnomAD_exomes_controls_NFE_AC = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_NFE_AF = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_NFE_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_NFE_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_POPMAX_AC = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_POPMAX_AF = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_POPMAX_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_POPMAX_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_SAS_AC = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_SAS_AF = functionalAnnotationArray[pos++];
        // 309
        String gnomAD_exomes_controls_SAS_AN = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_SAS_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_controls_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_exomes_flag = functionalAnnotationArray[pos++];
        String gnomAD_exomes_nhomalt = functionalAnnotationArray[pos++];
        addNumberToJsonObject("AC", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        addNumberToJsonObject("AF", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        addNumberToJsonObject("AFR_AC", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        addNumberToJsonObject("AFR_AF", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        String gnomAD_genomes_AFR_AN = functionalAnnotationArray[pos++];
        // 319
        String gnomAD_genomes_AFR_nhomalt = functionalAnnotationArray[pos++];
        addNumberToJsonObject("AMR_AC", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        addNumberToJsonObject("AMR_AF", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        String gnomAD_genomes_AMR_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_AMR_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_ASJ_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_ASJ_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_ASJ_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_ASJ_nhomalt = functionalAnnotationArray[pos++];
        // 329
        addNumberToJsonObject("EAS_AC", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        addNumberToJsonObject("EAS_AF", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        String gnomAD_genomes_EAS_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_EAS_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_FIN_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_FIN_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_FIN_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_FIN_nhomalt = functionalAnnotationArray[pos++];
        addNumberToJsonObject("NFE_AC", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        addNumberToJsonObject("NFE_AF", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        // 339
        String gnomAD_genomes_NFE_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_NFE_nhomalt = functionalAnnotationArray[pos++];
        addNumberToJsonObject("POPMAX_AC", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        addNumberToJsonObject("POPMAX_AF", functionalAnnotationArray[pos++] , frequencyGnomadGen, true);
        String gnomAD_genomes_POPMAX_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_POPMAX_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AFR_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AFR_AF = functionalAnnotationArray[pos++];
        // 349
        String gnomAD_genomes_controls_AFR_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AFR_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AMR_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AMR_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AMR_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AMR_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_ASJ_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_ASJ_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_ASJ_AN = functionalAnnotationArray[pos++];
        // 359
        String gnomAD_genomes_controls_ASJ_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_EAS_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_EAS_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_EAS_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_EAS_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_FIN_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_FIN_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_FIN_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_FIN_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_NFE_AC = functionalAnnotationArray[pos++];
        // 369
        String gnomAD_genomes_controls_NFE_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_NFE_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_NFE_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_POPMAX_AC = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_POPMAX_AF = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_POPMAX_AN = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_POPMAX_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_controls_nhomalt = functionalAnnotationArray[pos++];
        String gnomAD_genomes_flag = functionalAnnotationArray[pos++];
        String gnomAD_genomes_nhomalt = functionalAnnotationArray[pos++];
        // 379
        String hg18_chr = functionalAnnotationArray[pos++];
        String hg18_pos_1based = functionalAnnotationArray[pos++];
        String hg19_chr = functionalAnnotationArray[pos++];
        String hg19_pos_1based = functionalAnnotationArray[pos++];
        String integrated_confidence_value = functionalAnnotationArray[pos++];
        String integrated_fitCons_rankscore = functionalAnnotationArray[pos++];
        String integrated_fitCons_score = functionalAnnotationArray[pos++];
        String phastCons100way_vertebrate = functionalAnnotationArray[pos++];
        String phastCons100way_vertebrate_rankscore = functionalAnnotationArray[pos++];
        String phastCons17way_primate = functionalAnnotationArray[pos++];
        // 389
        String phastCons17way_primate_rankscore = functionalAnnotationArray[pos++];
        String phastCons30way_mammalian = functionalAnnotationArray[pos++];
        String phastCons30way_mammalian_rankscore = functionalAnnotationArray[pos++];
        String phyloP100way_vertebrate = functionalAnnotationArray[pos++];
        String phyloP100way_vertebrate_rankscore = functionalAnnotationArray[pos++];
        String phyloP17way_primate = functionalAnnotationArray[pos++];
        String phyloP17way_primate_rankscore = functionalAnnotationArray[pos++];
        String phyloP30way_mammalian = functionalAnnotationArray[pos++];
        String phyloP30way_mammalian_rankscore = functionalAnnotationArray[pos++];
        String pos_1_based = functionalAnnotationArray[pos++];
        // 399
//        String ref = functionalAnnotationArray[pos++];
        addStrToJsonObject("ref", functionalAnnotationArray[pos++], funcAnnotation, false);
//        String refcodon = functionalAnnotationArray[pos++];
        addStrToJsonObject("ref_codon", functionalAnnotationArray[pos++], funcAnnotation, false);
        String rs_dbSNP151 = functionalAnnotationArray[pos++];
        // 402

        //System.out.println("csqArray.length="+csqArray.length);
        //String[] functionalAnnotationArray = csqArray[i].split("|");


        /* population frequencies Json Obj */



        frequencies.put("1000Gp3", frequency1000Gp3);
        frequencies.put("ExAc", frequencyExAc);
        frequencies.put("gnomAD_exomes", frequencyGnomadEx);
        frequencies.put("gnomAD_genomes", frequencyGnomadGen);
        frequencies.put("ESP6500", frequencyEsp6500);
        funcAnnotation.put("frequencies", frequencies);
        funcAnnotation.put("predictions", prediction);
        //funcAnnotation.put("properties", funcAnnoProperties);
        //funcAnnotation.(funcAnnoProperties);


        if ((boolean) frequencyGnomadEx.get("available")) System.out.println(funcAnnotation.toString(2));
        //if ((boolean) frequencyGnomadGen.get("available")) System.out.println(frequencyGnomadGen.toString(2));

//        System.out.print("\n");

        return funcAnnotation;

    }

    private static boolean addNumberToJsonObject(String popName, String var, JSONObject freqObject, boolean withAvailability) {
        boolean avail = false;
        if (var.length()>0 && !".".equalsIgnoreCase(var)) {
            try {
                freqObject.put(popName, NF.parse(var));
                avail = true;
                if (withAvailability) freqObject.put("available", avail);
            } catch (ParseException parseException) {
                parseException.printStackTrace();
                if (withAvailability) freqObject.put("available", avail);
            }
        } else if (withAvailability) {
            freqObject.put("available", avail);

        }
        return avail;
    }

    private static boolean addStrToJsonObject(String popName, String var, JSONObject freqObject, boolean withAvailability) {
        boolean avail = false;
        if (var.length()>0 && !".".equalsIgnoreCase(var)) {
            freqObject.put(popName, var);
            avail = true;
            if (withAvailability) freqObject.put("available", avail);
        } else if (withAvailability) {
            freqObject.put("available", avail);
        }
        return avail;

    }



}
