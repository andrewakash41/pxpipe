/**
 * Vachan Cloud Functions — all in asia-south1 (Mumbai) for data residency.
 */
import { setGlobalOptions } from "firebase-functions/v2";

setGlobalOptions({ region: "asia-south1", maxInstances: 20 });

export { publishProfile, updatePrivacySettings } from "./callables/profile.js";
export { getDailyPicks, getProfileCard, passProfile, searchProfiles } from "./callables/discovery.js";
export { closeMatch, getSharedContact, respondToInterest, sendInterest, setContactSharing } from "./callables/interests.js";
export {
  aiAssistant, aiDraftBio, aiIcebreakers, aiKindDecline, aiMatchInsight, aiParseSearch, aiTranslate,
} from "./callables/ai.js";
export {
  acceptFamilyInvite, blockUser, cancelAccountDeletion, confirmWorkEmail, exportMyData, inviteFamilyManager,
  removeFamilyManager, reportUser, requestAccountDeletion, sendWorkEmailCode, startGovIdVerification,
  submitSelfieCheck, unblockUser, updateConsent, updateFamilyManager,
} from "./callables/account.js";
export { expireInterests, onMessageCreated, purgeDeletedAccounts } from "./triggers/index.js";
