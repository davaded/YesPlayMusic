/**
 * Browser-friendly alert wrapper.
 *
 * @param {string} message
 */
const nativeAlert = message => {
  alert(message);
};

export default nativeAlert;
